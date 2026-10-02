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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.sonnhalde.fotobox.AppVersion
import ch.sonnhalde.fotobox.MAX_COPIES
import ch.sonnhalde.fotobox.UiState
import ch.sonnhalde.fotobox.camera.CameraSetupPanel
import ch.sonnhalde.fotobox.kiosk.Kiosk
import ch.sonnhalde.fotobox.photo.BannerStyle
import ch.sonnhalde.fotobox.photo.PhotoComposer
import ch.sonnhalde.fotobox.wcm.WcmStatus
import kotlin.math.roundToInt

enum class SetupTab(val label: String) {
    Config("Konfiguration"), Camera("Kamera"), Printer("Drucker"), Queue("Warteschlange"), Wifi("WLAN"),
}

/**
 * Setup-Ansicht im Nicht-Retail-Modus (nach dem Vorbild der UpReach-Oberflaeche): Reiter oben,
 * Inhalt in der Mitte, grosser START-Knopf unten.
 */
@Composable
fun SetupScreen(state: UiState, actions: Actions) {
    var tab by rememberSaveable { mutableStateOf(SetupTab.Config) }
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
            Text("SONN", color = Sonn.Cream, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
            Text("HALDE", color = Sonn.LogoGold, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in SetupTab.values()) {
                    val active = t == tab
                    Text(
                        t.label + if (t == SetupTab.Queue && state.queueCount > 0) " (${state.queueCount})" else "",
                        color = if (active) Sonn.Navy else Color(0xFFC8D2DC), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.clip(MaterialShape).background(if (active) Sonn.HeadingGold else Color.Transparent)
                            .clickable { tab = t }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            // Ausschalten-Symbol: App beenden (nach PIN)
            Text("⏻", color = Sonn.Cream, fontSize = 22.sp, modifier = Modifier.clickable { askExit = true }.padding(8.dp))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.message?.let { Notice(it) }
            when (tab) {
                SetupTab.Config -> ConfigTab(state, actions)
                SetupTab.Camera -> {
                    SectionTitle("Kamera", "Kamera-Einstellungen")
                    CameraSetupPanel(
                        useFront = cfg.useFrontCamera, exposureIndex = cfg.exposureIndex, zoomRatio = cfg.zoomRatio,
                        onFront = { actions.onConfigQuiet(cfg.copy(useFrontCamera = it)) },
                        onExposure = { actions.onConfigQuiet(cfg.copy(exposureIndex = it)) },
                        onZoom = { actions.onConfigQuiet(cfg.copy(zoomRatio = it)) },
                    )
                }
                SetupTab.Printer -> {
                    ConnectionSection(state, actions.onConfigChange, actions.onCheckConnection, actions.onTestPrinter)
                    PrinterWifiSection(state, actions)
                    PrintFormatSection(state, actions)
                }
                SetupTab.Queue -> {
                    SectionTitle("SharePoint", "Upload-Warteschlange")
                    StatusLine(if (state.queueCount == 0) Sonn.Ok else Sonn.Error, "Wartende Fotos: ${state.queueCount}")
                    Text("Fotos, die nicht hochgeladen werden konnten (z. B. kein Internet), warten hier und werden erneut gesendet.", color = Sonn.Stone)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SonnButton("Jetzt senden", primary = true, onClick = actions.onRetryQueue)
                        SonnButton("Warteschlange leeren", primary = false, onClick = actions.onClearQueue)
                    }
                }
                SetupTab.Wifi -> {
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
private fun <T> ChoiceRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
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

@Composable
private fun ConfigTab(state: UiState, actions: Actions) {
    val context = LocalContext.current
    val cfg = state.config
    var newPin by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Foto", "Banner unten")
        SonnField("Text im Banner links", cfg.bannerTextLeft, KeyboardType.Text) {
            actions.onConfigQuiet(cfg.copy(bannerTextLeft = it.take(PhotoComposer.MAX_TEXT)))
        }
        Text("${cfg.bannerTextLeft.length}/${PhotoComposer.MAX_TEXT} Zeichen", color = Sonn.Stone, fontSize = 12.sp)
        SonnField("Text im Banner rechts (z. B. Personalfest 2027)", cfg.bannerText, KeyboardType.Text) {
            actions.onConfigQuiet(cfg.copy(bannerText = it.take(PhotoComposer.MAX_TEXT)))
        }
        Text("${cfg.bannerText.length}/${PhotoComposer.MAX_TEXT} Zeichen", color = Sonn.Stone, fontSize = 12.sp)

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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Retail-Modus", "Startbildschirm")
        SonnField("Überschrift", cfg.startTitle, KeyboardType.Text, singleLine = false) { actions.onConfigQuiet(cfg.copy(startTitle = it.take(60))) }
        SonnField("Text des Start-Knopfs", cfg.startButton, KeyboardType.Text) { actions.onConfigQuiet(cfg.copy(startButton = it.take(24))) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(actions.onPickBackground) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonnButton("Hintergrundbild wählen", primary = false, onClick = { picker.launch("image/*") })
            SonnButton("Entfernen", primary = false, onClick = actions.onClearBackground)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Ablauf", "Timer und Druck")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Timer vor dem Foto", Modifier.weight(1f))
            for (sec in listOf(3, 5, 10)) {
                SonnButton("$sec s", primary = cfg.timerSeconds == sec, onClick = { actions.onConfigQuiet(cfg.copy(timerSeconds = sec)) })
            }
        }
        SettingSwitch("Foto automatisch drucken (1 Abzug)", cfg.autoPrint) { actions.onConfigQuiet(cfg.copy(autoPrint = it)) }
    }

    UploadSection(state, actions)

    QrSection(state, actions.onQrInput, actions.onGenerate, actions.onDownload)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Sicherheit", "PIN für Retail-Modus und Beenden")
        Text("Kiosk: " + if (Kiosk.isDeviceOwner(context)) "Device Owner (volle Sperre)" else "kein Device Owner – nur «Bildschirm fixieren»", color = Sonn.Stone, fontSize = 13.sp)
        SonnField("Neue PIN (mind. 4 Zeichen)", newPin, KeyboardType.NumberPassword) { newPin = it }
        SonnButton("PIN speichern", primary = false, onClick = { actions.onSetPin(newPin); newPin = "" })
    }
}
