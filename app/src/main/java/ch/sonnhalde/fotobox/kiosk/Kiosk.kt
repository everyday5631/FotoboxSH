package ch.sonnhalde.fotobox.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Kiosk-Modus (= Retail-Modus) wie bei UpReach: Lock-Task-Modus (App kann nicht verlassen werden), Vollbild, Bildschirm bleibt an.
 *
 * - Als **Device Owner** (einmalig per ADB gesetzt) startet der Lock-Task-Modus ohne Rueckfrage und ohne
 *   Ausstiegsgeste. Befehl: `adb shell dpm set-device-owner ch.sonnhalde.fotobox/.kiosk.AdminReceiver`
 * - Ohne Device Owner nutzt Android «Bildschirm anheften»: Das System fragt einmal nach, Ausstieg per Gestik.
 */
object Kiosk {
    private const val PREFS = "kiosk"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PIN = "pin"
    const val DEFAULT_PIN = "1234"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, false)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()
    fun pin(c: Context): String = prefs(c).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
    fun setPin(c: Context, pin: String) = prefs(c).edit().putString(KEY_PIN, pin).apply()

    fun isDeviceOwner(c: Context): Boolean =
        (c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isDeviceOwnerApp(c.packageName)

    fun isLocked(c: Context): Boolean {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** Kurzbeschreibung fuer die Uebersicht. */
    fun statusText(c: Context) =
        if (isDeviceOwner(c)) "Kiosk: Device Owner (volle Sperre)"
        else "Kiosk: kein Device Owner – nur «Bildschirm fixieren» möglich"

    /** Vollbild + Bildschirm an; bei aktivem Kiosk zusaetzlich Lock-Task. */
    fun apply(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isEnabled(activity)) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
        if (isEnabled(activity)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            start(activity)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun start(activity: Activity) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(activity.packageName)) {
            val admin = ComponentName(activity, AdminReceiver::class.java)
            // Druckdialog des Systems muss im Lock-Task-Modus erlaubt sein.
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName, "com.android.printspooler"))
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }
        if (!isLocked(activity)) runCatching { activity.startLockTask() }
    }

    /** Kiosk beenden (nur nach PIN-Eingabe aufrufen). */
    fun exit(activity: Activity) {
        setEnabled(activity, false)
        runCatching { activity.stopLockTask() }
        apply(activity)
    }
}
