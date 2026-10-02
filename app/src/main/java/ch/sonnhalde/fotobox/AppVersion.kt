package ch.sonnhalde.fotobox

/** Versionsanzeige, z. B. «Version 0.1.17 (d7d4df8)»; die Zahl ist die Build-Nummer der CI. */
object AppVersion {
    val label: String get() = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})"
}
