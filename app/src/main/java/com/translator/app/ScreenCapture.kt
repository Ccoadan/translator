package com.translator.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics

/**
 * Captura la pantalla del teléfono usando MediaProjection API.
 * Devuelve un Bitmap que luego se pasa al OCR.
 */
class ScreenCapture(
    private val context: Context,
    resultCode: Int,
    data: Intent
) {

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = projectionManager.getMediaProjection(resultCode, data)
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // Dimensiones de la pantalla
    private val metrics = context.resources.displayMetrics
    private val screenWidth = metrics.widthPixels
    private val screenHeight = metrics.heightPixels
    private val screenDensity = metrics.densityDpi

    fun iniciar() {
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    /**
     * Captura un frame de la pantalla y lo devuelve como Bitmap.
     * Retorna null si no hay imagen disponible todavía.
     */
    fun capturar(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null

        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Recortar al tamaño exacto de la pantalla
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } finally {
            image.close()
        }
    }

    fun detener() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
