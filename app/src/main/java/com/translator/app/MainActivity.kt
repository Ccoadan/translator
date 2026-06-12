
package com.translator.app
 
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.translator.app.databinding.ActivityMainBinding
 
class MainActivity : AppCompatActivity() {
 
    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
 
    private val SCREEN_CAPTURE_REQUEST = 100
    private val OVERLAY_PERMISSION_REQUEST = 101
 
    private var inactivitySeconds = 4
    private var isRunning = false
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
 
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
 
        setupSlider()
        setupButton()
    }
 
    private fun setupSlider() {
        binding.sliderSeconds.min = 2
        binding.sliderSeconds.max = 10
        binding.sliderSeconds.progress = inactivitySeconds
        updateSecondsLabel(inactivitySeconds)
 
        binding.sliderSeconds.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                inactivitySeconds = progress
                updateSecondsLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
 
    private fun updateSecondsLabel(seconds: Int) {
        binding.labelSeconds.text = "$seconds segundos de inactividad"
    }
 
    private fun setupButton() {
        binding.btnStartStop.setOnClickListener {
            if (isRunning) {
                detenerServicio()
            } else {
                verificarPermisosYArrancar()
            }
        }
    }
 
    private fun verificarPermisosYArrancar() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Necesito permiso para mostrar sobre otras apps", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
            return
        }
        pedirPermisoCapturaPantalla()
    }
 
    private fun pedirPermisoCapturaPantalla() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST)
    }
 
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
 
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST -> {
                if (Settings.canDrawOverlays(this)) {
                    pedirPermisoCapturaPantalla()
                } else {
                    Toast.makeText(this, "Sin ese permiso la app no puede funcionar", Toast.LENGTH_LONG).show()
                }
            }
 
            SCREEN_CAPTURE_REQUEST -> {
                if (resultCode == RESULT_OK && data != null) {
                    iniciarServicio(resultCode, data)
                } else {
                    Toast.makeText(this, "Permiso de pantalla rechazado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
 
    private fun iniciarServicio(resultCode: Int, data: Intent) {
        // Guardar datos en companion object antes de iniciar el servicio
        TranslatorService.pendingResultCode = resultCode
        TranslatorService.pendingData = data
 
        val serviceIntent = Intent(this, TranslatorService::class.java).apply {
            putExtra("inactivitySeconds", inactivitySeconds)
        }
 
        startForegroundService(serviceIntent)
 
        isRunning = true
        binding.btnStartStop.text = "Detener"
        binding.sliderSeconds.isEnabled = false
 
        // Ir al inicio
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }
 
    private fun detenerServicio() {
        val serviceIntent = Intent(this, TranslatorService::class.java)
        stopService(serviceIntent)
 
        isRunning = false
        binding.btnStartStop.text = "Iniciar"
        binding.sliderSeconds.isEnabled = true
    }
 
    override fun onResume() {
        super.onResume()
        isRunning = TranslatorService.isRunning
        if (isRunning) {
            binding.btnStartStop.text = "Detener"
            binding.sliderSeconds.isEnabled = false
        } else {
            binding.btnStartStop.text = "Iniciar"
            binding.sliderSeconds.isEnabled = true
        }
    }
}
