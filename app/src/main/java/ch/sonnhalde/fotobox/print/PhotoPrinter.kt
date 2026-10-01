package ch.sonnhalde.fotobox.print

import android.content.Context
import android.graphics.Bitmap
import androidx.print.PrintHelper

/**
 * Druckt ueber den normalen Android-Druckdialog. Der WCMPlus-Drucker (DNP QW410) erscheint dort,
 * sobald Tablet und WCMPlus im selben WLAN sind; im Dialog Papierformat «4 x 6 in.» waehlen.
 */
object PhotoPrinter {
    fun print(context: Context, bitmap: Bitmap) {
        PrintHelper(context).apply {
            scaleMode = PrintHelper.SCALE_MODE_FIT
            colorMode = PrintHelper.COLOR_MODE_COLOR
            orientation = if (bitmap.width >= bitmap.height) PrintHelper.ORIENTATION_LANDSCAPE
            else PrintHelper.ORIENTATION_PORTRAIT
        }.printBitmap("Sonnhalde Foto", bitmap)
    }
}
