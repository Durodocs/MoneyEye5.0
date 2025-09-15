package com.example.moneyeye

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.*
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: java.util.concurrent.ExecutorService
    private lateinit var tts: TextToSpeech
    private val handler = Handler(Looper.getMainLooper())

    private var processing = false
    private var isSpeaking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts.language = Locale("es", "ES")
        }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { imageProxy -> processImageProxy(imageProxy) } }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Error al iniciar cámara", exc)
                runOnUiThread { Toast.makeText(this, "Error al iniciar la cámara.", Toast.LENGTH_SHORT).show() }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ✅ Aquí aplicamos @OptIn para usar imageProxy.image sin errores
    @OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (processing || isSpeaking) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        processing = true

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.uppercase(Locale.ROOT)
                val amount = when {
                    Regex("\\$?1\\b|ONE").containsMatchIn(text) -> "1 dólar"
                    Regex("\\$?5\\b|FIVE").containsMatchIn(text) -> "5 dólares"
                    Regex("\\$?10\\b|TEN").containsMatchIn(text) -> "10 dólares"
                    Regex("\\$?20\\b|TWENTY").containsMatchIn(text) -> "20 dólares"
                    Regex("\\$?50\\b|FIFTY").containsMatchIn(text) -> "50 dólares"
                    Regex("\\$?100\\b|HUNDRED").containsMatchIn(text) -> "100 dólares"
                    else -> null
                }
                if (amount != null && !isSpeaking) speakText("Detectado: $amount")
            }
            .addOnFailureListener { Log.e(TAG, "Error reconociendo texto", it) }
            .addOnCompleteListener { processing = false; imageProxy.close() }
    }

    private fun speakText(text: String) {
        isSpeaking = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        handler.postDelayed({ isSpeaking = false }, 3000)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) startCamera()
            else { Toast.makeText(this, "Permisos denegados.", Toast.LENGTH_SHORT).show(); finish() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "MoneyEye"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}