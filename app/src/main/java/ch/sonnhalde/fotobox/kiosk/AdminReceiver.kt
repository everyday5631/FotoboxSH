package ch.sonnhalde.fotobox.kiosk

import android.app.admin.DeviceAdminReceiver

/** Device-Admin-Receiver; Voraussetzung, damit die App als Device Owner den Kiosk-Modus erhalten kann. */
class AdminReceiver : DeviceAdminReceiver()
