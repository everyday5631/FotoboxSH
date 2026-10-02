package ch.sonnhalde.fotobox.print

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Verbindet voruebergehend mit dem WLAN des Druckers (WCMPlus-Hotspot), fuehrt den Druck aus und gibt das Netz
 * danach wieder frei; Android verbindet dann von selbst zurueck ins vorherige WLAN (z. B. SH-GAST).
 *
 * Seit Android 10 duerfen Apps das WLAN nicht mehr frei umschalten. «WifiNetworkSpecifier» ist der vorgesehene Weg:
 * Android fragt beim Verbinden um Erlaubnis; der Datenverkehr der App wird an dieses Netz gebunden.
 */
class PrinterNetwork(private val context: Context) {
    suspend fun <T> use(ssid: String, password: String, timeoutMs: Int = 30_000, block: suspend (Network) -> T): Result<T> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val spec = WifiNetworkSpecifier.Builder().setSsid(ssid).apply { if (password.isNotBlank()) setWpa2Passphrase(password) }.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        var callback: ConnectivityManager.NetworkCallback? = null
        return try {
            val network = withTimeoutOrNull(timeoutMs + 2_000L) {
                suspendCancellableCoroutine<Network?> { cont ->
                    val cb = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) { if (cont.isActive) cont.resume(network) }
                        override fun onUnavailable() { if (cont.isActive) cont.resume(null) }
                    }
                    callback = cb
                    cm.requestNetwork(request, cb, timeoutMs)
                    cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
                }
            } ?: return Result.failure(
                IllegalStateException("Drucker-WLAN «$ssid» nicht erreichbar (Verbindung nicht erlaubt, Passwort falsch oder ausser Reichweite)"),
            )
            Result.success(block(network))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Freigeben: Android kehrt danach ins vorherige WLAN zurueck.
            callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        }
    }
}
