package com.translator.app
 
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import kotlinx.coroutines.*
 
class TranslatorService : Service() {
 
    companion object {
        var isRunning = false
        var pendingResultCode: Int = -1
        var pendingData: Intent? = null
        const val CHANNEL_ID = "translator_channel"
        const val NOTIFICATION_ID = 1
    }
 
    private var screenCapture: ScreenCapture? = null
    private var ocrTranslator: OcrTranslator? = null
    private var overlayManager: OverlayManager? = null
    private var inactivityDetector: InactivityDetector? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var procesandoCaptura = false
 
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inactivitySeconds = intent?.getIntExtra("inactivitySeconds", 4) ?: 4
 
        // Crear notificación primero (requerido en Android 14+)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
 
        // Esperar un momento y luego inicializar con los datos guardados
        serviceScope.launch {
            delay(500) // Pequeña pausa para que el sistema registre el foreground service
 
            val resultCode = pendingResultCode
            val data = pendingData
 
            if (resultCode == -1 || data == null) {
                isRunning = false
                stopSelf()
                return@launch
            }
 
            isRunning = true
            inicializarComponentes(resultCode, data, inactivitySeconds)
        }
 
        return START_NOT_STICKY
    }
 
    private fun inicializarComponentes(resultCode: Int, data: Intent, inactivitySeconds: Int) {
        try {
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
 
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
            stopSelf()
        }
    }
 
    private suspend fun capturarYTraducir() {
        if (procesandoCaptura) return
        procesandoCaptura = true
 
        withContext(Dispatchers.IO) {
            delay(150)
 
            val bitmap = try {
                screenCapture?.capturar()
            } catch (e: Exception) {
                null
            }
 
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
        pendingData = null
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
