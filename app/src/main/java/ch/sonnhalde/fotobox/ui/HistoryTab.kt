package ch.sonnhalde.fotobox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.sonnhalde.fotobox.MAX_COPIES
import ch.sonnhalde.fotobox.UiState
import ch.sonnhalde.fotobox.photo.HistoryEntry
import ch.sonnhalde.fotobox.photo.PhotoHistory
import ch.sonnhalde.fotobox.qr.QrCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reiter «Fotos» im Setup: die letzten Fotos mit Vorschau, erneutem Drucken und Hochladen, Export und Loeschen.
 * Die Gaeste sehen diese Liste nie.
 */
@Composable
internal fun HistoryTab(state: UiState, actions: Actions) {
    val context = LocalContext.current
    val store = remember { PhotoHistory(context) }
    val cfg = state.config
    // Beim Oeffnen und nach Aenderung der Grenzen: aufraeumen und neu einlesen.
    LaunchedEffect(cfg.historyCount, cfg.historyHours) { actions.onRefreshHistory() }

    var openId by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Fotos", "Foto-Verlauf")
        Text(
            "Die letzten ${cfg.historyCount} Fotos (Druck- und digitale Version) bleiben höchstens ${cfg.historyHours} Stunden auf dem Tablet, " +
                "damit ein Foto bei einem Druckfehler oder nach dem Zeitlimit noch einmal gedruckt oder hochgeladen werden kann. " +
                "Ist die Anzahl erreicht, wird mit jedem neuen Foto das älteste gelöscht. Gäste sehen diese Liste nicht.",
            color = Sonn.Stone, fontSize = 13.sp,
        )
        ChoiceRow("Anzahl Fotos im Verlauf", listOf(3, 6, 10, 20).map { "$it" to it }, cfg.historyCount) { actions.onConfigQuiet(cfg.copy(historyCount = it)) }
        ChoiceRow("Aufbewahrung in Stunden", listOf(6, 12, 24, 48, 72).map { "$it h" to it }, cfg.historyHours) { actions.onConfigQuiet(cfg.copy(historyHours = it)) }

        if (state.history.isEmpty()) Text("Noch keine Fotos im Verlauf.", color = Sonn.Stone)
        for (e in state.history) HistoryRow(e, store) { openId = e.id }
        if (state.history.isNotEmpty()) SonnButton("Alle löschen …", primary = false, onClick = { confirmClear = true })
    }

    val open = state.history.firstOrNull { it.id == openId }
    if (open != null) {
        HistoryDetail(open, store, actions, onClose = { openId = null }, askPin = { pinAction = it })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Alle Fotos im Verlauf löschen?") },
            text = { Text("Druck- und digitale Version aller Fotos werden vom Tablet gelöscht. Das lässt sich nicht rückgängig machen. Wartende Druckaufträge bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; pinAction = actions.onClearHistory }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } },
        )
    }
    pinAction?.let { action ->
        PinDialog(title = "PIN eingeben", onDismiss = { pinAction = null },
            onSubmit = { pin -> actions.onCheckPin(pin).also { ok -> if (ok) { pinAction = null; action() } } })
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, store: PhotoHistory, onClick: () -> Unit) {
    val thumb = remember(e.id) { store.thumbnail(e.id) }
    Row(
        Modifier.fillMaxWidth().border(1.dp, Sonn.Line, MaterialShape).background(Sonn.Surface).clickable(onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 120.dp, height = 80.dp).clip(MaterialShape).background(Sonn.Line)) {
            if (thumb != null) Image(thumb.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(timeText(e.createdAt), fontWeight = FontWeight.Bold)
            val (printColor, printText) = printLabel(e)
            Text(printText, color = printColor, fontSize = 14.sp)
            val (uploadColor, uploadText) = uploadLabel(e)
            Text(uploadText, color = uploadColor, fontSize = 14.sp)
        }
        Text("›", color = Sonn.Stone, fontSize = 24.sp)
    }
}

@Composable
private fun HistoryDetail(e: HistoryEntry, store: PhotoHistory, actions: Actions, onClose: () -> Unit, askPin: (() -> Unit) -> Unit) {
    val big = remember(e.id) { store.image(e.id, 1400) }
    var copies by remember(e.id) { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(timeText(e.createdAt)) },
        confirmButton = { TextButton(onClick = onClose) { Text("Schliessen") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (big != null) Image(big.asImageBitmap(), contentDescription = "Druck-Version", modifier = Modifier.fillMaxWidth().border(1.dp, Sonn.Line, MaterialShape))
                val (printColor, printText) = printLabel(e)
                Text("Druck: $printText", color = printColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (e.printDetail.isNotBlank()) Text(e.printDetail, color = Sonn.Stone, fontSize = 12.sp)
                val (uploadColor, uploadText) = uploadLabel(e)
                Text("Download: $uploadText", color = uploadColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (e.uploadUrl.isNotBlank()) {
                    val qr = remember(e.uploadUrl) { QrCode.generate(e.uploadUrl, 400) }
                    Box(Modifier.border(1.dp, Sonn.Line, MaterialShape).background(Color.White).padding(8.dp)) {
                        Image(qr.asImageBitmap(), contentDescription = "QR-Code", modifier = Modifier.size(180.dp))
                    }
                    Text(e.uploadUrl, color = Sonn.Stone, fontSize = 11.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SonnButton("−", primary = false, onClick = { copies = (copies - 1).coerceIn(1, MAX_COPIES) })
                    Text("$copies ${if (copies == 1) "Abzug" else "Abzüge"}", fontWeight = FontWeight.Bold)
                    SonnButton("+", primary = false, onClick = { copies = (copies + 1).coerceIn(1, MAX_COPIES) })
                }
                SonnButton("Erneut drucken", primary = true, onClick = { actions.onReprint(e.id, copies); onClose() })
                Text("Bei einem Fehler wird bis zu zweimal mit je 1 Abzug neu versucht.", color = Sonn.Stone, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SonnButton("Erneut hochladen", primary = false, onClick = { actions.onReupload(e.id); onClose() })
                    SonnButton("Exportieren", primary = false, onClick = { actions.onExportEntry(e.id); onClose() })
                }
                SonnButton("Löschen …", primary = false, onClick = { askPin { actions.onDeleteEntry(e.id); onClose() } })
            }
        },
    )
}

private fun printLabel(e: HistoryEntry): Pair<Color, String> = when (e.printStatus) {
    "done" -> Sonn.Ok to "gedruckt"
    "printing" -> Sonn.Stone to "Druck läuft …"
    "pending" -> Sonn.Stone to "Druckauftrag wartet"
    "failed" -> Sonn.Error to "NICHT gedruckt (Fehler)"
    else -> Sonn.Stone to "nicht gedruckt"
}

private fun uploadLabel(e: HistoryEntry): Pair<Color, String> = when (e.uploadStatus) {
    "done" -> Sonn.Ok to "hochgeladen"
    "failed" -> Sonn.Error to "Upload fehlgeschlagen"
    else -> Sonn.Stone to "Upload offen"
}

private fun timeText(millis: Long): String = SimpleDateFormat("dd.MM.yyyy, HH:mm:ss", Locale.GERMAN).format(Date(millis))
