package ch.sonnhalde.fotobox.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.sonnhalde.fotobox.AppVersion
import ch.sonnhalde.fotobox.MAX_COPIES
import ch.sonnhalde.fotobox.R
import ch.sonnhalde.fotobox.UiState
import ch.sonnhalde.fotobox.camera.CameraSetupPanel
import ch.sonnhalde.fotobox.kiosk.Kiosk
import ch.sonnhalde.fotobox.photo.BackgroundLibrary
import ch.sonnhalde.fotobox.photo.BannerStyle
import ch.sonnhalde.fotobox.photo.Graphemes
import ch.sonnhalde.fotobox.photo.PhotoComposer
import ch.sonnhalde.fotobox.wcm.WcmStatus
import kotlin.math.roundToInt

/**
 * Reihenfolge von links nach rechts: je weiter links, desto technischer (einmal einrichten),
 * je weiter rechts, desto naeher am Alltag (je Anlass anpassen, Fotos ansehen).
 */
enum class SetupTab(val label: String) {
    System("System"), Printer("Drucker"), Upload("QR & Upload"), Camera("Kamera"), Flow("Ablauf"), Look("Aussehen"), Photos("Fotos"),
}

/**
 * Setup-Ansicht im Nicht-Retail-Modus (nach dem Vorbild der UpReach-Oberflaeche): Reiter oben,
 * Inhalt in der Mitte, grosser START-Knopf unten.
 */
@Composable
fun SetupScreen(state: UiState, actions: Actions) {
    var tab by rememberSaveable { mutableStateOf(SetupTab.Look) }
    var askExit by remember { mutableStateOf(false) }
    if (askExit) {
        PinDialog(title = "App beenden", onDismiss = { askExit = false },
            onSubmit = { pin -> actions.onCheckPin(pin).also { ok -> if (ok) { askExit = false; actions.onExitApp() } } })
    }
    val cfg = state.config

    Column(Modifier.fillMaxSize().background(Sonn.Cream)) {
        // Kopfleiste mit Reitern
        Row(
            Modifier.fillMaxWidth().background(Sonn.Navy).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.sonnhalde_logo_zusatz_neg),
                contentDescription = "Sonnhalde, Pflege- und Betreuungszentrum",
                modifier = Modifier.height(34.dp),
            )
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in SetupTab.values()) {
                    val active = t == tab
                    Text(
                        t.label + when {
                            t == SetupTab.Upload && state.queueCount > 0 -> " (${state.queueCount})"
                            // Hinweis fuer das Personal: ein Foto wurde nicht gedruckt.
                            t == SetupTab.Photos && state.history.any { it.printStatus == "failed" } -> " (!)"
                            else -> ""
                        },
                        color = if (active) Sonn.Navy else Color(0xFFC8D2DC), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.clip(MaterialShape).background(if (active) Sonn.HeadingGold else Color.Transparent)
                            .clickable { tab = t }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            // Kennzeichnung: das hier ist die Einrichtung, nicht die Gaesteansicht.
            Text(
                "ENTWICKLERMODUS", color = Sonn.LogoGold, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 8.dp).border(1.dp, Sonn.LogoGold, MaterialShape).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            // Ausschalten-Symbol: App beenden (nach PIN)
            Icon(
                painter = painterResource(R.drawable.ic_power), contentDescription = "App beenden", tint = Sonn.Cream,
                modifier = Modifier.clickable { askExit = true }.padding(8.dp).size(28.dp),
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.message?.let { Notice(it) }
            when (tab) {
                SetupTab.System -> SystemTab(state, actions)
                SetupTab.Printer -> {
                    ConnectionSection(state, actions.onConfigChange, actions.onCheckConnection, actions.onTestPrinter)
                    PrinterWifiSection(state, actions)
                    PrintFormatSection(state, actions)
                }
                SetupTab.Upload -> UploadTab(state, actions)
                SetupTab.Camera -> {
                    SectionTitle("Kamera", "Kamera-Einstellungen")
                    CameraSetupPanel(
                        useFront = cfg.useFrontCamera, exposureIndex = cfg.exposureIndex, zoomRatio = cfg.zoomRatio,
                        onFront = { actions.onConfigQuiet(cfg.copy(useFrontCamera = it)) },
                        onExposure = { actions.onConfigQuiet(cfg.copy(exposureIndex = it)) },
                        onZoom = { actions.onConfigQuiet(cfg.copy(zoomRatio = it)) },
                    )
                }
                SetupTab.Flow -> FlowTab(state, actions)
                SetupTab.Look -> LookTab(state, actions)
                SetupTab.Photos -> HistoryTab(state, actions)
            }
        }

        // Unten: grosser Start-Knopf
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = actions.onStartRetail,
                shape = MaterialShape,
                colors = ButtonDefaults.buttonColors(containerColor = Sonn.NavyDeep, contentColor = Sonn.Cream),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("Start", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Text("Aktive Konfiguration: SONNHALDE · ${AppVersion.label}", color = Sonn.Stone, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
internal fun <T> ChoiceRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((text, value) in options) SonnButton(text, primary = value == selected, onClick = { onSelect(value) })
        }
    }
}

/** Zum Drucken automatisch ins Drucker-WLAN wechseln (und danach zurueck ins Internet-WLAN fuer den QR-Code). */
@Composable
private fun PrinterWifiSection(state: UiState, actions: Actions) {
    val cfg = state.config
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Drucker", "Drucker-WLAN automatisch")
        Text(
            "Für den Fall, dass das Tablet im Internet-WLAN (z. B. SH-GAST) den Drucker nicht sieht: Beim Drucken verbindet sich die App " +
                "kurz mit dem WLAN des Druckers und danach wieder zurück. Beim ersten Mal fragt Android um Erlaubnis.",
            color = Sonn.Stone, fontSize = 13.sp,
        )
        SettingSwitch("Zum Drucken ins Drucker-WLAN wechseln", cfg.printerWifiSwitch) { actions.onConfigQuiet(cfg.copy(printerWifiSwitch = it)) }
        SonnField("Name des Drucker-WLANs (z. B. WCMPLUS-aed)", cfg.printerWifiSsid, KeyboardType.Text) { actions.onConfigQuiet(cfg.copy(printerWifiSsid = it)) }
        SonnField("Passwort des Drucker-WLANs", cfg.printerWifiPassword, KeyboardType.Password) { actions.onConfigQuiet(cfg.copy(printerWifiPassword = it)) }
        SonnField("Adresse des Druckers im Drucker-WLAN", cfg.printerWifiHost, KeyboardType.Uri) { actions.onConfigQuiet(cfg.copy(printerWifiHost = it)) }
        SonnButton("Drucker-WLAN testen", primary = false, onClick = actions.onTestPrinterWifi)
        state.printerTest?.let { Text(it, color = Sonn.Stone, fontSize = 13.sp) }
    }
}

/** Druckformat wie im Android-Dialog: Papier, Skalierung, Ausrichtung, Drehung; mit Testdruck zum Ausprobieren. */
@Composable
private fun PrintFormatSection(state: UiState, actions: Actions) {
    val cfg = state.config
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Drucker", "Druckformat")
        // Beim WCMPlus gibt es pro Papierformat eine eigene Druckwarteschlange (z. B. QW410-4x6 und QW410-4x4).
        LaunchedEffect(Unit) { if (state.printerQueues.isEmpty()) actions.onTestPrinter() }
        if (state.printerQueues.isNotEmpty()) {
            ChoiceRow("Druckformat (Warteschlange des Druckers)", listOf("Automatisch (4x6)" to "") + state.printerQueues.map { it to it }, cfg.printerQueue) {
                actions.onConfigQuiet(cfg.copy(printerQueue = it))
            }
        } else {
            Text("Druckwarteschlangen werden gesucht … (Drucker eingeschaltet und im selben WLAN?)", color = Sonn.Stone, fontSize = 13.sp)
            SonnButton("Erneut suchen", primary = false, onClick = actions.onTestPrinter)
        }
        Text("Das Papierformat steckt im Namen der Warteschlange (4x6 = 10 x 15 cm).", color = Sonn.Stone, fontSize = 12.sp)
        ChoiceRow("Skalierung", listOf("Einpassen" to "fit", "Füllen (zuschneiden)" to "fill", "Drucker entscheidet" to "auto"), cfg.printScaling) {
            actions.onConfigQuiet(cfg.copy(printScaling = it))
        }
        ChoiceRow("Ausrichtung", listOf("Automatisch" to "auto", "Quer" to "landscape", "Hoch" to "portrait", "Nicht senden" to "none"), cfg.printOrientation) {
            actions.onConfigQuiet(cfg.copy(printOrientation = it))
        }
        ChoiceRow("Bild drehen", listOf("0°" to 0, "90°" to 90, "180°" to 180, "270°" to 270), cfg.printRotation) {
            actions.onConfigQuiet(cfg.copy(printRotation = it))
        }
        SonnButton("Testdruck (Foto mit Banner)", primary = true, onClick = actions.onTestPrint)
        Text("Kunden können bis zu $MAX_COPIES Abzüge drucken.", color = Sonn.Stone, fontSize = 13.sp)
    }
}

/** Upload-Ziel: Power Automate (SharePoint) oder Nextcloud. */
@Composable
private fun UploadSection(state: UiState, actions: Actions) {
    val cfg = state.config
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Download", "Upload-Ziel für QR-Code")
        ChoiceRow("Ziel", listOf("SharePoint (Power Automate)" to "flow", "Nextcloud" to "nextcloud"), cfg.uploadTarget) {
            actions.onConfigQuiet(cfg.copy(uploadTarget = it))
        }
        if (cfg.uploadTarget == "nextcloud") {
            SonnField("Nextcloud-Adresse (https://…)", cfg.ncUrl, KeyboardType.Uri) { actions.onConfigQuiet(cfg.copy(ncUrl = it)) }
            SonnField("Benutzername", cfg.ncUser, KeyboardType.Text) { actions.onConfigQuiet(cfg.copy(ncUser = it)) }
            SonnField("App-Passwort", cfg.ncPassword, KeyboardType.Password) { actions.onConfigQuiet(cfg.copy(ncPassword = it)) }
            SonnField("Ordner (wird angelegt)", cfg.ncFolder, KeyboardType.Text) { actions.onConfigQuiet(cfg.copy(ncFolder = it)) }
            ChoiceRow(
                "Download-Link gültig für",
                listOf("1 Std." to 1, "6 Std." to 6, "12 Std." to 12, "24 Std." to 24, "2 Tage" to 48, "7 Tage" to 168, "ohne Ablauf" to 0),
                cfg.ncShareHours,
            ) { actions.onConfigQuiet(cfg.copy(ncShareHours = it)) }
            SettingSwitch("Link nach Ablauf sofort ungültig machen (stundengenau)", cfg.ncRevokeLinks) {
                actions.onConfigQuiet(cfg.copy(ncRevokeLinks = it))
            }
            Text(
                "Nextcloud selbst kennt nur ganze Tage (die Anzeige dort zeigt immer «23:59 Uhr»). Mit diesem Schalter widerruft die App jeden Link " +
                    "zur eingestellten Zeit selbst (solange die App läuft, sonst beim nächsten Upload); die Fotos bleiben in Nextcloud liegen. " +
                    "Betroffen sind nur Links auf Fotos dieser App (foto-…/test-…) im oben genannten Ordner.",
                color = Sonn.Stone, fontSize = 12.sp,
            )
        } else {
            SonnField("Upload-Link (Power-Automate-Flow)", cfg.flowUrl, KeyboardType.Uri) { actions.onConfigQuiet(cfg.copy(flowUrl = it)) }
        }
        SonnButton("Upload testen", primary = false, onClick = actions.onTestUpload)
        state.uploadTestText?.let {
            Text(it, color = if (it.startsWith("✗")) Sonn.Error else Sonn.Navy, fontSize = 14.sp)
        }
        state.uploadTestQr?.let { qr ->
            Box(Modifier.border(1.dp, Sonn.Line, MaterialShape).background(androidx.compose.ui.graphics.Color.White).padding(10.dp)) {
                Image(qr.asImageBitmap(), contentDescription = "QR-Code zum Testfoto", modifier = Modifier.size(200.dp))
            }
        }
    }
}

/** Aussehen: was die Gaeste sehen (Banner, Foto-Hintergruende, Startbildschirm). Wird je Anlass angepasst. */
@Composable
private fun LookTab(state: UiState, actions: Actions) {
    val context = LocalContext.current
    val cfg = state.config

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Foto", "Banner unten")
        SonnField("Text im Banner links", cfg.bannerTextLeft, KeyboardType.Text) {
            actions.onConfigQuiet(cfg.copy(bannerTextLeft = Graphemes.take(it, PhotoComposer.MAX_TEXT)))
        }
        Text("${Graphemes.count(cfg.bannerTextLeft)}/${PhotoComposer.MAX_TEXT} Zeichen (Emoji sind erlaubt)", color = Sonn.Stone, fontSize = 12.sp)
        SonnField("Text im Banner rechts (z. B. Personalfest 2027)", cfg.bannerText, KeyboardType.Text) {
            actions.onConfigQuiet(cfg.copy(bannerText = Graphemes.take(it, PhotoComposer.MAX_TEXT)))
        }
        Text("${Graphemes.count(cfg.bannerText)}/${PhotoComposer.MAX_TEXT} Zeichen (Emoji sind erlaubt)", color = Sonn.Stone, fontSize = 12.sp)

        Text("Textfarbe", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in listOf("black" to "Schwarz", "gold" to "Gold", "navy" to "Navy", "rainbow" to "Regenbogen")) {
                SonnButton(label, primary = cfg.bannerColor == key, onClick = { actions.onConfigQuiet(cfg.copy(bannerColor = key)) })
            }
        }

        var alpha by remember(cfg.bannerAlpha) { mutableFloatStateOf(cfg.bannerAlpha.toFloat()) }
        Text("Banner-Hintergrund: ${alpha.roundToInt()} % deckend", fontWeight = FontWeight.Bold)
        Slider(
            value = alpha, onValueChange = { alpha = it },
            onValueChangeFinished = { actions.onConfigQuiet(cfg.copy(bannerAlpha = alpha.roundToInt())) },
            valueRange = 0f..100f, steps = 9,
            colors = SliderDefaults.colors(thumbColor = Sonn.Navy, activeTrackColor = Sonn.HeadingGold),
        )
        Text("Unter 100 % liegt der Banner durchscheinend auf dem Foto, bei 100 % unter dem Foto.", color = Sonn.Stone, fontSize = 12.sp)

        val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(actions.onPickLogo) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonnButton("Logo wählen (PNG/JPG)", primary = false, onClick = { logoPicker.launch("image/*") })
            SonnButton("Standard-Logo", primary = false, onClick = actions.onClearLogo)
        }
        SettingSwitch("Banner auch auf der digitalen Version (QR-Code)", cfg.bannerDigital) { actions.onConfigQuiet(cfg.copy(bannerDigital = it)) }

        val logoStamp = PhotoComposer.logoFile(context).lastModified()
        val style = BannerStyle.from(cfg)
        val preview = remember(style, logoStamp) { PhotoComposer.preview(context, style) }
        Text("Vorschau Druck", color = Sonn.Stone, fontSize = 12.sp)
        Image(preview.asImageBitmap(), contentDescription = "Vorschau Druck", modifier = Modifier.fillMaxWidth().border(1.dp, Sonn.Line, MaterialShape))
        if (cfg.bannerDigital) {
            val digital = remember(style, logoStamp) { PhotoComposer.previewDigital(context, style) }
            Text("Vorschau digital (QR-Code)", color = Sonn.Stone, fontSize = 12.sp)
            Image(digital.asImageBitmap(), contentDescription = "Vorschau digital", modifier = Modifier.fillMaxWidth().border(1.dp, Sonn.Line, MaterialShape))
        }
    }


    BackgroundSection(state, actions)


    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Retail-Modus", "Startbildschirm")
        SonnField("Überschrift", cfg.startTitle, KeyboardType.Text, singleLine = false) { actions.onConfigQuiet(cfg.copy(startTitle = Graphemes.take(it, 60))) }
        SonnField("Text des Start-Knopfs", cfg.startButton, KeyboardType.Text) { actions.onConfigQuiet(cfg.copy(startButton = Graphemes.take(it, 24))) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(actions.onPickBackground) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonnButton("Hintergrundbild wählen", primary = false, onClick = { picker.launch("image/*") })
            SonnButton("Entfernen", primary = false, onClick = actions.onClearBackground)
        }
    }

}

/** Ablauf fuer die Gaeste: Timer, Auto-Druck, Zeitlimit. */
@Composable
private fun FlowTab(state: UiState, actions: Actions) {
    val cfg = state.config

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Ablauf", "Timer und Druck")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Timer vor dem Foto", Modifier.weight(1f))
            for (sec in listOf(3, 5, 10)) {
                SonnButton("$sec s", primary = cfg.timerSeconds == sec, onClick = { actions.onConfigQuiet(cfg.copy(timerSeconds = sec)) })
            }
        }
        SettingSwitch("Foto automatisch drucken (1 Abzug)", cfg.autoPrint) { actions.onConfigQuiet(cfg.copy(autoPrint = it)) }

        var timeout by remember(cfg.resultTimeoutSeconds) { mutableFloatStateOf(cfg.resultTimeoutSeconds.toFloat()) }
        val timeoutShown = (timeout / 10).roundToInt() * 10
        Text("Ergebnis-Bildschirm: zurück zum Start nach $timeoutShown s ohne Eingabe", fontWeight = FontWeight.Bold)
        Slider(
            value = timeout, onValueChange = { timeout = it },
            onValueChangeFinished = { actions.onConfigQuiet(cfg.copy(resultTimeoutSeconds = (timeout / 10).roundToInt() * 10)) },
            valueRange = 30f..300f, steps = 26,
            colors = SliderDefaults.colors(thumbColor = Sonn.Navy, activeTrackColor = Sonn.HeadingGold),
        )
        Text("Solange gedruckt wird, läuft die Zeit nicht ab (höchstens 5 Minuten pro Druckschritt).", color = Sonn.Stone, fontSize = 12.sp)
    }

}

/** Upload-Ziel, wartende Uploads und QR-Code von Hand. */
@Composable
private fun UploadTab(state: UiState, actions: Actions) {
    UploadSection(state, actions)

    QueueSection(state, actions)

    QrSection(state, actions.onQrInput, actions.onGenerate, actions.onDownload)

}

/** Wartende Uploads (heute nur fehlgeschlagene Uploads; Druckauftraege stehen im Reiter «Fotos»). */
@Composable
private fun QueueSection(state: UiState, actions: Actions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Download", "Upload-Warteschlange")
        StatusLine(if (state.queueCount == 0) Sonn.Ok else Sonn.Error, "Wartende Fotos: ${state.queueCount}")
        Text("Fotos, die nicht hochgeladen werden konnten (z. B. kein Internet), warten hier und werden erneut gesendet. Nicht gedruckte Fotos stehen im Reiter «Fotos».", color = Sonn.Stone)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonnButton("Jetzt senden", primary = true, onClick = actions.onRetryQueue)
            SonnButton("Warteschlange leeren", primary = false, onClick = actions.onClearQueue)
        }
    }
}

/** System: Technik, die man einmal einrichtet (Netzwerk, Sicherheit, Freistellungs-Technik). */
@Composable
private fun SystemTab(state: UiState, actions: Actions) {
    val context = LocalContext.current
    var newPin by remember { mutableStateOf("") }

    WifiSection(state, actions)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Sicherheit", "PIN für Retail-Modus und Beenden")
        Text("Kiosk: " + if (Kiosk.isDeviceOwner(context)) "Device Owner (volle Sperre)" else "kein Device Owner – nur «Bildschirm fixieren»", color = Sonn.Stone, fontSize = 13.sp)
        SonnField("Neue PIN (mind. 4 Zeichen)", newPin, KeyboardType.NumberPassword) { newPin = it }
        SonnButton("PIN speichern", primary = false, onClick = { actions.onSetPin(newPin); newPin = "" })
    }

    ProcessingSection(state, actions)
}

/** Netzwerk: Android-WLAN und WCMPlus-Status. */
@Composable
private fun WifiSection(state: UiState, actions: Actions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Netzwerk", "WLAN")
        Text("Tablet und WCMPlus müssen im selben WLAN sein. Das Netz des WCMPlus-Moduls heisst «WCMPLUS-…»; nach der Einrichtung hängt es im lokalen WLAN.", color = Sonn.Stone)
        SonnButton("Android-WLAN-Einstellungen öffnen", primary = true, onClick = actions.onOpenWifi)
        val (dot, text) = when (val s = state.status) {
            WcmStatus.Unknown -> Sonn.Stone to "WCMPlus: noch nicht geprüft"
            WcmStatus.Checking -> Sonn.Stone to "WCMPlus: wird geprüft …"
            is WcmStatus.Online -> Sonn.Ok to ("WCMPlus: verbunden" + (s.via?.let { " ($it)" } ?: ""))
            is WcmStatus.Offline -> Sonn.Error to "WCMPlus: nicht erreichbar"
        }
        StatusLine(dot, text)
        SonnButton("Erneut prüfen", primary = false, onClick = actions.onCheckConnection)
    }
}

/** Foto-Hintergrund wie bei Teams: Personen werden freigestellt und vor das gewaehlte Bild gesetzt. */
@Composable
private fun BackgroundSection(state: UiState, actions: Actions) {
    val context = LocalContext.current
    val cfg = state.config
    val library = remember { BackgroundLibrary(context) }
    val items = remember(state.backgroundsVersion) { library.all() }
    val selected = cfg.backgroundId.takeIf { id -> items.any { it.id == id } }.orEmpty()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(actions.onAddBackground) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Foto", "Hintergrund")
        Text(
            "Die Personen werden auf dem Tablet freigestellt und vor das gewählte Bild gesetzt (z. B. Wald, Fest, Logo). " +
                "Das Live-Bild der Kamera zeigt weiterhin den echten Hintergrund; der neue Hintergrund erscheint im fertigen Foto " +
                "(Druck und QR-Code). Ohne Auswahl bleibt der echte Hintergrund.",
            color = Sonn.Stone, fontSize = 12.sp,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundTile(selected = selected.isEmpty(), label = "Original", onClick = { actions.onConfigQuiet(cfg.copy(backgroundId = "")) }) {
                Text("Kein\nHintergrund", color = Sonn.Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            for (item in items) {
                BackgroundTile(selected = item.id == selected, label = item.label, onClick = { actions.onConfigQuiet(cfg.copy(backgroundId = item.id)) }) {
                    val thumb = remember(item.id, state.backgroundsVersion) { library.thumbnail(item.id) }
                    if (thumb != null) {
                        Image(thumb.asImageBitmap(), contentDescription = item.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(item.label, color = Sonn.Stone, fontSize = 12.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonnButton("Bild hinzufügen (Galerie)", primary = false, onClick = { picker.launch("image/*") })
            val custom = items.firstOrNull { it.id == selected && !it.builtIn }
            if (custom != null) SonnButton("Ausgewähltes Bild löschen", primary = false, onClick = { actions.onDeleteBackground(custom.id) })
        }
        SettingSwitch("Gäste wählen den Hintergrund beim Fotografieren", cfg.backgroundGuestChoice) { actions.onConfigQuiet(cfg.copy(backgroundGuestChoice = it)) }
        Text(
            if (cfg.backgroundGuestChoice) "Das Bild oben ist die Vorauswahl; Gäste können auf dem Kamera-Bildschirm ein anderes antippen."
            else "Gäste sehen keine Auswahl; es gilt immer das Bild oben.",
            color = Sonn.Stone, fontSize = 12.sp,
        )
        Text("Die mitgelieferten Bilder sind Platzhalter in den Sonnhalde-Farben; eigene Bilder lassen sich hinzufügen und wieder löschen.", color = Sonn.Stone, fontSize = 12.sp)
    }
}

/** Technik der Freistellung: Haarmodell und Diagnose-Zeile (fuer Tests und Fehlersuche). */
@Composable
private fun ProcessingSection(state: UiState, actions: Actions) {
    val cfg = state.config
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Foto", "Freistellung (technisch)")
        SettingSwitch("Haarmodell verwenden", cfg.backgroundHairModel) { actions.onConfigQuiet(cfg.copy(backgroundHairModel = it)) }
        Text(
            if (cfg.backgroundHairModel) "Ergänzt Haare bei der Freistellung, braucht etwas mehr Rechenzeit. Ausschalten, wenn das Foto zu lange dauert; es gilt dann nur die Personen-Erkennung."
            else "Aus: nur die Personen-Erkennung, schneller, Haare können weniger genau sein.",
            color = Sonn.Stone, fontSize = 12.sp,
        )
        SettingSwitch("Diagnose-Zeile zum Hintergrund auf dem Ergebnis (für Tests)", cfg.backgroundDiagnostics) { actions.onConfigQuiet(cfg.copy(backgroundDiagnostics = it)) }
        Text("Zeigt nach jedem Foto, welcher Hintergrund galt, wie viel des Bildes als Person erkannt wurde und wie lange die Freistellung gedauert hat. Im Einsatz ausschalten.", color = Sonn.Stone, fontSize = 12.sp)
    }
}

@Composable
private fun BackgroundTile(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.size(width = 132.dp, height = 112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 132.dp, height = 92.dp)
                .clip(MaterialShape)
                .background(Sonn.Surface)
                .border(if (selected) 3.dp else 1.dp, if (selected) Sonn.HeadingGold else Sonn.Line, MaterialShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
        Text(label, color = if (selected) Sonn.Navy else Sonn.Stone, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}
