package com.translator.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

/**
 * Servicio principal que corre en segundo plano.
 * Une todos los componentes: InactivityDetector, ScreenCapture, OcrTranslator y OverlayManager.
 *
 * Flujo:
 * 1. InactivityDetector detecta que no hay movimiento por X segundos
 * 2. ScreenCapture toma una captura de pantalla
 * 3. OcrTranslator lee el texto y lo traduce
 * 4. OverlayManager muestra las traducciones encima del texto original
 * 5. Cuando hay movimiento de nuevo → OverlayManager limpia los overlays
 */
class TranslatorService : Service() {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "translator_channel"
        const val NOTIFICATION_ID = 1
    }

    private var screenCapture: ScreenCapture? = null
    private var ocrTranslator: OcrTranslator? = null
    private var overlayManager: OverlayManager? = null
    private var inactivityDetector: InactivityDetector? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Evitar que se disparen múltiples capturas a la vez
    private var procesandoCaptura = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        val inactivitySeconds = intent?.getIntExtra("inactivitySeconds", 4) ?: 4

        if (resultCode == -1 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        inicializarComponentes(resultCode, data, inactivitySeconds)

        return START_NOT_STICKY
    }

    private fun inicializarComponentes(resultCode: Int, data: Intent, inactivitySeconds: Int) {
        screenCapture = ScreenCapture(this, resultCode, data).also { it.iniciar() }
        ocrTranslator = OcrTranslator()
        overlayManager = OverlayManager(this)

        inactivityDetector = InactivityDetector(
            context = this,
            inactivitySeconds = inactivitySeconds,
            onInactive = {
                serviceScope.launch { capturarYTraducir() }
            },
            onActive = {
                serviceScope.launch(Dispatchers.Main) {
                    overlayManager?.limpiarOverlays()
                    procesandoCaptura = false
                }
            }
        ).also { it.start() }
    }

    private suspend fun capturarYTraducir() {
        if (procesandoCaptura) return
        procesandoCaptura = true

        withContext(Dispatchers.IO) {
            delay(100)

            val bitmap = screenCapture?.capturar()

            if (bitmap == null) {
                procesandoCaptura = false
                return@withContext
            }

            ocrTranslator?.procesarBitmap(bitmap) { bloques ->
                serviceScope.launch(Dispatchers.Main) {
                    if (bloques.isNotEmpty()) {
                        overlayManager?.mostrarTraducciones(bloques)
                    }
                    procesandoCaptura = false
                    bitmap.recycle()
                }
            } ?: run {
                procesandoCaptura = false
                bitmap.recycle()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        procesandoCaptura = false
        inactivityDetector?.stop()
        overlayManager?.limpiarOverlays()
        screenCapture?.detener()
        ocrTranslator?.liberar()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Traductor activo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "El traductor está corriendo en segundo plano"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Traductor activo")
            .setContentText("Deja de mover el teléfono para ver la traducción")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
}
