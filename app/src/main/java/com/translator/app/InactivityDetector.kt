package com.translator.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Detecta inactividad del teléfono usando el acelerómetro.
 * Cuando no hay movimiento por X segundos, llama a onInactive().
 * Cuando hay movimiento, llama a onActive().
 */
class InactivityDetector(
    private val context: Context,
    private val inactivitySeconds: Int,
    private val onInactive: () -> Unit,
    private val onActive: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Último valor del acelerómetro
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastMovementTime = System.currentTimeMillis()

    // Si ya notificamos que está inactivo (para no llamar onInactive múltiples veces)
    private var isCurrentlyInactive = false

    // Hilo que revisa cada segundo si ya pasó el tiempo de inactividad
    private var checkThread: Thread? = null
    private var running = false

    // Sensibilidad: movimiento menor a este valor se ignora (evita activarse con vibraciones mínimas)
    private val MOVEMENT_THRESHOLD = 0.3f

    fun start() {
        running = true
        lastMovementTime = System.currentTimeMillis()
        isCurrentlyInactive = false

        // Registrar el sensor
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        // Hilo que revisa cada 500ms si pasó el tiempo de inactividad
        checkThread = Thread {
            while (running) {
                val elapsed = (System.currentTimeMillis() - lastMovementTime) / 1000
                if (elapsed >= inactivitySeconds && !isCurrentlyInactive) {
                    isCurrentlyInactive = true
                    onInactive()
                }
                Thread.sleep(500)
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        checkThread?.interrupt()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)

        // Si hay movimiento real (no solo ruido del sensor)
        if (deltaX > MOVEMENT_THRESHOLD || deltaY > MOVEMENT_THRESHOLD || deltaZ > MOVEMENT_THRESHOLD) {
            lastMovementTime = System.currentTimeMillis()

            // Si estaba inactivo, notificar que volvió a moverse
            if (isCurrentlyInactive) {
                isCurrentlyInactive = false
                onActive()
            }
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No necesitamos hacer nada aquí
    }
}
