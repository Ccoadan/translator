package com.translator.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Muestra rectángulos de traducción encima del texto original.
 * Cada bloque de texto detectado recibe su propio overlay en la misma posición.
 */
class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Lista de views actualmente mostrados
    private val overlaysActivos = mutableListOf<View>()

    /**
     * Muestra las traducciones encima del texto original.
     * Cada TextBlock tiene su posición (bounds) en pantalla.
     */
    fun mostrarTraducciones(bloques: List<TextBlock>) {
        // Limpiar overlays anteriores primero
        limpiarOverlays()

        for (bloque in bloques) {
            mostrarBloque(bloque)
        }
    }

    private fun mostrarBloque(bloque: TextBlock) {
        val bounds = bloque.bounds

        // Crear el TextView que mostrará la traducción
        val textView = TextView(context).apply {
            text = bloque.translatedText
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(230, 20, 20, 20)) // Fondo oscuro semitransparente
            textSize = calcularTamanoTexto(bounds)
            setPadding(8, 4, 8, 4)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // Parámetros de posición: exactamente encima del texto original
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }

        try {
            windowManager.addView(textView, params)
            overlaysActivos.add(textView)
        } catch (e: Exception) {
            // Si falla agregar un overlay, continuar con los demás
            e.printStackTrace()
        }
    }

    /**
     * Calcula un tamaño de texto proporcional al bloque detectado.
     */
    private fun calcularTamanoTexto(bounds: Rect): Float {
        val alturaBloque = bounds.height()
        return when {
            alturaBloque < 30 -> 10f
            alturaBloque < 50 -> 12f
            alturaBloque < 80 -> 14f
            alturaBloque < 120 -> 16f
            else -> 18f
        }
    }

    /**
     * Elimina todos los overlays de traducción.
     * Se llama cuando el usuario vuelve a tocar la pantalla.
     */
    fun limpiarOverlays() {
        for (view in overlaysActivos) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlaysActivos.clear()
    }

    fun hayOverlaysActivos(): Boolean = overlaysActivos.isNotEmpty()
}
