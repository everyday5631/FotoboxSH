package ch.sonnhalde.fotobox.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import ch.sonnhalde.fotobox.Screen
import ch.sonnhalde.fotobox.UiState
import ch.sonnhalde.fotobox.UploadState
import ch.sonnhalde.fotobox.camera.CameraScreen
import ch.sonnhalde.fotobox.print.PhotoPrinter
import java.io.File
import ch.sonnhalde.fotobox.wcm.WcmConfig
import ch.sonnhalde.fotobox.wcm.WcmStatus

class Actions(
    val onConfigChange: (WcmConfig) -> Unit,
    val onCheckConnection: () -> Unit,
    val onQrInput: (String) -> Unit,
    val onGenerate: () -> Unit,
    val onDownload: () -> Unit,
    val onOpenCamera: () -> Unit,
    val onPhotoCaptured: (File) -> Unit,
    val onCameraError: (String) -> Unit,
    val onRetryUpload: () -> Unit,
    val onHome: () -> Unit,
)

@Composable
fun MainScreen(state: UiState, actions: Actions) {
    if (state.screen == Screen.Camera) {
        CameraScreen(onCaptured = actions.onPhotoCaptured, onCancel = actions.onHome, onFailure = actions.onCameraError)
        return
    }
    Column(Modifier.fillMaxSize().background(Sonn.Cream)) {
        SonnhaldeBanner(title = "Fotobox")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.screen == Screen.Result) {
                ResultSection(state, actions)
            } else {
                state.message?.let { Notice(it) }
                SonnButton("Foto aufnehmen", primary = true, onClick = actions.onOpenCamera)
                ConnectionSection(state, actions.onConfigChange, actions.onCheckConnection)
                QrSection(state, actions.onQrInput, actions.onGenerate, actions.onDownload)
            }
        }
    }
}

@Composable
private fun ResultSection(state: UiState, actions: Actions) {
    val context = LocalContext.current
    SectionTitle("Fertig", "Ihr Foto")
    state.photo?.let { photo ->
        Image(photo.asImageBitmap(), contentDescription = "Foto", modifier = Modifier.fillMaxWidth().border(1.dp, Sonn.Line, MaterialShape))
        SonnButton("Drucken", primary = true, onClick = { PhotoPrinter.print(context, photo) })
        Text("Im Druckdialog den Drucker «QW410» und das Papierformat «4 x 6 in.» wählen.", color = Sonn.Stone, fontSize = 13.sp)
    }
    when (val u = state.upload) {
        UploadState.Idle -> Unit
        UploadState.Uploading -> Text("Foto wird nach SharePoint hochgeladen …", color = Sonn.Stone)
        is UploadState.Failed -> {
            Notice(u.reason)
            SonnButton("Upload wiederholen", primary = false, onClick = actions.onRetryUpload)
        }
        is UploadState.Done -> {
            SectionTitle("Download", "QR-Code zum Foto")
            state.qrBitmap?.let { bmp ->
                Box(Modifier.fillMaxWidth().border(1.dp, Sonn.Line, MaterialShape).background(Color.White).padding(16.dp), contentAlignment = Alignment.Center) {
                    Image(bmp.asImageBitmap(), contentDescription = "QR-Code", modifier = Modifier.size(260.dp))
                }
                SonnButton("QR-Code herunterladen", primary = false, onClick = actions.onDownload)
            }
        }
    }
    state.message?.let { Notice(it) }
    SonnButton("Neues Foto", primary = true, onClick = actions.onHome)
}

/** Ueberschrift mit Gold-Linie (GoldRule-Muster des Design-Systems). */
@Composable
internal fun SectionTitle(eyebrow: String, title: String) {
    Column {
        Text(eyebrow.uppercase(), color = Sonn.GoldDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(title, color = Sonn.Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        Box(Modifier.padding(top = 8.dp).size(width = 48.dp, height = 3.dp).background(Sonn.HeadingGold))
    }
}

@Composable
private fun ConnectionSection(state: UiState, onConfigChange: (WcmConfig) -> Unit, onCheck: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(state.config) { mutableStateOf(state.config) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Drucker", "WCMPlus-Verbindung")

        val (dot, text) = when (val s = state.status) {
            WcmStatus.Unknown -> Sonn.Stone to "Noch nicht geprüft"
            WcmStatus.Checking -> Sonn.Stone to "Verbindung wird geprüft …"
            is WcmStatus.Online -> Sonn.Ok to "Verbunden (HTTP ${s.httpCode})"
            is WcmStatus.Offline -> Sonn.Error to "Nicht erreichbar: ${s.reason}"
        }
        Row(
            Modifier.fillMaxWidth().border(1.dp, Sonn.Line, MaterialShape).background(Sonn.Surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Column(Modifier.weight(1f)) {
                Text(text, fontWeight = FontWeight.Bold)
                Text(state.config.baseUrl, color = Sonn.Stone, fontSize = 13.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SonnButton("Erneut prüfen", onClick = onCheck, primary = true)
            SonnButton(if (editing) "Schliessen" else "Einstellungen", onClick = { editing = !editing }, primary = false)
        }

        if (editing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SonnField("Adresse von WCMPlus", draft.baseUrl, KeyboardType.Uri) { draft = draft.copy(baseUrl = it) }
                SonnField("Pfad für Verbindungstest", draft.statusPath, KeyboardType.Uri) { draft = draft.copy(statusPath = it) }
                SonnField("Token (optional)", draft.token, KeyboardType.Password) { draft = draft.copy(token = it) }
                SonnField("Upload-Link (Power-Automate-Flow für SharePoint)", draft.flowUrl, KeyboardType.Uri) { draft = draft.copy(flowUrl = it) }
                SonnButton("Speichern", primary = true, onClick = { onConfigChange(draft); editing = false })
            }
        }
    }
}

@Composable
private fun QrSection(state: UiState, onInput: (String) -> Unit, onGenerate: () -> Unit, onDownload: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("SharePoint", "QR-Code erstellen")
        Text(
            "Link einer SharePoint-Datei oder eines Ordners einfügen – oder in SharePoint «Teilen» wählen und diese App auswählen.",
            color = Sonn.Stone,
        )
        SonnField("SharePoint-Link", state.qrInput, KeyboardType.Uri, singleLine = false) { onInput(it) }
        SonnButton("QR-Code erzeugen", primary = true, onClick = onGenerate)

        state.qrBitmap?.let { bmp ->
            Box(Modifier.fillMaxWidth().border(1.dp, Sonn.Line, MaterialShape).background(Color.White).padding(16.dp), contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), contentDescription = "QR-Code", modifier = Modifier.size(260.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SonnButton("Herunterladen", primary = true, onClick = onDownload)
                if (state.savedUri != null) {
                    SonnButton("Teilen", primary = false, onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, state.savedUri as Uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "QR-Code teilen"))
                    })
                }
            }
        }
        state.message?.let { Notice(it) }
    }
}

@Composable
internal fun Notice(text: String) {
    Box(Modifier.fillMaxWidth().background(Sonn.WarnBg).padding(12.dp)) {
        Text(text, color = Sonn.Warn, fontSize = 14.sp)
    }
}

internal val MaterialShape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)

@Composable
internal fun SonnButton(label: String, primary: Boolean, onDark: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        Button(
            onClick = onClick,
            shape = MaterialShape,
            colors = ButtonDefaults.buttonColors(containerColor = Sonn.HeadingGold, contentColor = Sonn.Navy),
        ) { Text(label, fontWeight = FontWeight.Bold) }
    } else {
        val tone = if (onDark) Sonn.Cream else Sonn.Navy
        OutlinedButton(
            onClick = onClick,
            shape = MaterialShape,
            border = BorderStroke(1.5.dp, tone),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = tone),
        ) { Text(label, fontWeight = FontWeight.Bold) }
    }
}

@Composable
internal fun SonnField(label: String, value: String, keyboard: KeyboardType, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Sonn.Navy,
            unfocusedBorderColor = Sonn.Line,
            focusedLabelColor = Sonn.GoldDeep,
            cursorColor = Sonn.Navy,
            focusedContainerColor = Sonn.Surface,
            unfocusedContainerColor = Sonn.Surface,
        ),
    )
}
