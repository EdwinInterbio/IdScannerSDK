package com.ibi.scannersdk

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceScannerActivity : androidx.activity.ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var isCaptured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kita tidak pakai layout XML agar SDK lebih ringan, kita buat PreviewView secara kode
        val previewView = androidx.camera.view.PreviewView(this)
        setContentView(previewView)

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera(previewView)
    }

    private fun startCamera(previewView: androidx.camera.view.PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            // Analisis Frame Kamera
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                // Gunakan LENS_FACING_FRONT untuk selfie wajah
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture, imageAnalyzer
                )
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isCaptured) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()

            val detector = FaceDetection.getClient(options)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    // Jika wajah terdeteksi, ambil bitmap LANGSUNG dari frame ini (lebih cepat)
                    if (faces.isNotEmpty() && !isCaptured) {
                        isCaptured = true

                        val bitmap = imageProxy.yuvToBitmap()
                        val base64 = bitmapToBase64(bitmap)

                        val resultIntent = android.content.Intent().apply {
                            putExtra("BASE64_FACE", base64)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun ImageProxy.yuvToBitmap(): android.graphics.Bitmap {
        val yBuffer = planes[0].buffer // Y
        val vBuffer = planes[2].buffer // V
        val uBuffer = planes[1].buffer // U

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, this.width, this.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height), 90, out)
        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Putar bitmap sesuai orientasi sensor HP
        val matrix = android.graphics.Matrix()
        matrix.postRotate(this.imageInfo.rotationDegrees.toFloat())
        // Mirroring karena kita pakai kamera depan
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

        return android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // 1. Resize Gambar (Misal: Max lebar/tinggi 640px agar tetap jelas tapi ringan)
        val resizedBitmap = getResizedBitmap(bitmap, 640)

        val outputStream = ByteArrayOutputStream()

        // 2. Compress Kualitas (Gunakan 60-70 untuk keseimbangan size vs kualitas)
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)

        val byteArray = outputStream.toByteArray()

        // Gunakan NO_WRAP agar string Base64 tidak mengandung karakter 'newline' (\n)
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    // Fungsi Helper untuk Resize
    private fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.width
        var height = image.height

        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    // Helper untuk merubah ImageProxy ke Bitmap
    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
