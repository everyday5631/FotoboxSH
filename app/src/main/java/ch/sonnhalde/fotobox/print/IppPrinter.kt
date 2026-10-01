package ch.sonnhalde.fotobox.print

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import ch.sonnhalde.fotobox.wcm.WcmConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Druckt ein JPEG direkt per IPP auf den Drucker hinter WCMPlus, ohne Android-Druckdialog.
 *
 * Die genaue IPP-Adresse des QW410 ist nicht dokumentiert (im Android-Dialog erscheint er als
 * «QW410-4x6 @ dnpimage», IP 192.168.4.1). Deshalb wird entweder die in den Einstellungen eingetragene
 * Adresse verwendet oder eine Liste ueblicher Pfade durchprobiert; der erste Treffer wird gemerkt.
 */
class IppPrinter(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    data class Found(val ippUri: String, val httpUrl: String, val info: Ipp.Response)

    @Volatile private var cached: Found? = null

    /** Sucht den Drucker; liefert bei Misserfolg eine Fehlerbeschreibung mit allen Versuchen. */
    suspend fun discover(config: WcmConfig): Result<Found> = withContext(Dispatchers.IO) {
        cached?.takeIf { config.printerUri.isBlank() }?.let { return@withContext Result.success(it) }
        val tried = mutableListOf<String>()
        for (candidate in candidates(config)) {
            val (ipp, httpUrl) = normalize(candidate)
            val attempt = runCatching { post(httpUrl, Ipp.getPrinterAttributes(ipp)) }
            val resp = attempt.getOrNull()
            if (resp != null && resp.ok) {
                return@withContext Result.success(Found(ipp, httpUrl, resp).also { cached = it })
            }
            tried += "$ipp → " + (resp?.statusText() ?: attempt.exceptionOrNull()?.message ?: "keine Antwort")
        }
        Result.failure(IllegalStateException("Drucker nicht gefunden:\n" + tried.joinToString("\n")))
    }

    suspend fun printJpeg(config: WcmConfig, jpeg: ByteArray, landscape: Boolean, copies: Int = 1): Result<String> {
        val printer = discover(config).getOrElse { return Result.failure(it) }
        val formats = printer.info.attrs["document-format-supported"].orEmpty()
        if (formats.isNotEmpty() && "image/jpeg" !in formats) {
            return Result.failure(IllegalStateException("Drucker nimmt kein JPEG an (unterstützt: ${formats.joinToString()})"))
        }
        val media = printer.info.attrs["media-supported"].orEmpty().firstOrNull { it.contains("4x6") }
        // Vom genauesten zum einfachsten Auftrag; manche Drucker lehnen einzelne Attribute ab.
        val attempts = listOf(
            Ipp.JobOptions(media, scalingFill = true, landscape = landscape, copies = copies),
            Ipp.JobOptions(media, copies = copies),
            Ipp.JobOptions(copies = copies),
        ).distinct()
        var lastError = "unbekannt"
        for (options in attempts) {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    post(printer.httpUrl, Ipp.printJob(printer.ippUri, "Sonnhalde Foto", "image/jpeg", options, jpeg))
                }
            }
            val resp = result.getOrNull()
            if (resp != null && resp.ok) return Result.success(if (copies > 1) "$copies Abzüge an den Drucker gesendet." else "Foto an den Drucker gesendet.")
            lastError = resp?.statusText() ?: result.exceptionOrNull()?.message ?: lastError
        }
        cached = null // Adresse neu suchen, falls sich der Drucker bewegt hat
        return Result.failure(IllegalStateException("Drucker hat den Auftrag abgelehnt ($lastError)"))
    }

    private fun post(httpUrl: String, body: ByteArray): Ipp.Response {
        val request = Request.Builder().url(httpUrl)
            .post(body.toRequestBody("application/ipp".toMediaType())).build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return Ipp.parse(r.body?.bytes() ?: error("leere Antwort"))
        }
    }

    /**
     * Wie Mopria: Drucker per mDNS/Bonjour (_ipp._tcp) im WLAN suchen. Das liefert die echte Adresse inkl. Pfad,
     * auch wenn WCMPlus nicht unter 192.168.4.1 erreichbar ist (z. B. wenn es im lokalen WLAN haengt).
     */
    private suspend fun discoverMdns(): List<String> {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = java.util.concurrent.CopyOnWriteArrayList<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) { found += service }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        if (runCatching { nsd.discoverServices("_ipp._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }.isFailure) return emptyList()
        delay(3000)
        runCatching { nsd.stopServiceDiscovery(listener) }
        val result = mutableListOf<String>()
        // QW410/DNP/WCM zuerst; Aufloesungen nacheinander (Android erlaubt nur eine gleichzeitig).
        val ordered = found.sortedByDescending { it.serviceName.contains("QW410", true) || it.serviceName.contains("DNP", true) || it.serviceName.contains("WCM", true) }
        for (service in ordered.take(5)) resolve(nsd, service)?.let { result += it }
        return result
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, service: NsdServiceInfo): String? =
        withTimeoutOrNull(2500) {
            suspendCancellableCoroutine { cont ->
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(null) }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = (info.host as? Inet4Address)?.hostAddress
                        val rp = info.attributes["rp"]?.let { String(it) }.orEmpty().trim('/')
                        if (cont.isActive) cont.resume(host?.let { "ipp://$it:${info.port}/$rp" })
                    }
                })
            }
        }

    private suspend fun candidates(config: WcmConfig): List<String> {
        if (config.printerUri.isNotBlank()) return listOf(config.printerUri.trim())
        val mdns = discoverMdns()
        val host = runCatching { URI(config.baseUrl.trim()).host }.getOrNull() ?: return mdns
        return mdns + listOf("/ipp/print", "/printers/QW410-4x6", "/printers/dnpimage", "/printers/QW410", "/ipp", "/")
            .map { "ipp://$host:631$it" }
    }

    /** ipp://host[:port]/pfad -> (IPP-URI, HTTP-URL); Standardport 631. */
    private fun normalize(raw: String): Pair<String, String> {
        val uri = URI(raw.replace(Regex("^ipps?://", RegexOption.IGNORE_CASE), "http://"))
        val port = if (uri.port == -1) 631 else uri.port
        val path = uri.rawPath.ifEmpty { "/" }
        return "ipp://${uri.host}:$port$path" to "http://${uri.host}:$port$path"
    }
}
