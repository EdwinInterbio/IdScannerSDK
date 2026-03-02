package com.ibi.scannersdk

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.*
import android.view.View

class FaceScannerActivity : androidx.activity.ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var isCaptured = false
    private var stableFrameCount = 0
    private val REQUIRED_STABLE_FRAMES = 8
    private var cameraProvider: ProcessCameraProvider? = null

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        FaceDetection.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Container utama
        val rootLayout = android.widget.FrameLayout(this)

        // 1. Preview Kamera
        val previewView = androidx.camera.view.PreviewView(this)
        rootLayout.addView(previewView)

        // 2. Overlay Kotak (Ditumpuk di atas kamera)
        val overlayView = ScannerOverlayView(this)
        rootLayout.addView(overlayView)

        setContentView(rootLayout)

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera(previewView)
    }

    private fun startCamera(previewView: androidx.camera.view.PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val provider = cameraProvider ?: return@addListener

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
                provider.unbindAll()
                // Gunakan LENS_FACING_FRONT untuk selfie wajah
                provider.bindToLifecycle(
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

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0] // Ambil wajah pertama

                        // 1. CEK POSISI (Harus di tengah & ukurannya pas)
                        if (isFaceValid(face, imageProxy.width, imageProxy.height)) {
                            stableFrameCount++

                            // 2. CEK STABILITAS (Jika sudah diam selama X frame)
                            if (stableFrameCount >= REQUIRED_STABLE_FRAMES && !isCaptured) {
                                isCaptured = true
                                val bitmap = imageProxy.yuvToBitmap()
                                val base64 = bitmapToBase64(bitmap)

                                val resultIntent = android.content.Intent().apply {
                                    putExtra("BASE64_FACE", base64)
                                }
                                setResult(RESULT_OK, resultIntent)
                                cameraProvider?.unbindAll()
                                finish()
                            }
                        } else {
                            // Reset counter jika wajah bergerak keluar area atau terlalu jauh
                            stableFrameCount = 0
                        }
                    } else {
                        // Reset counter jika wajah hilang
                        stableFrameCount = 0
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun isFaceValid(face: com.google.mlkit.vision.face.Face, imgWidth: Int, imgHeight: Int): Boolean {
        val bounds = face.boundingBox

        // Karena koordinat ML Kit (ImageProxy) berbeda dengan koordinat Layar (PreviewView),
        // kita gunakan rasio sederhana untuk mengecek apakah wajah di tengah.

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        // Margin aman: wajah harus berada di area tengah 40% - 60% dari frame
        val horizontalCenter = centerX.toFloat() / imgWidth
        val verticalCenter = centerY.toFloat() / imgHeight

        val isCentered = horizontalCenter in 0.3..0.7 && verticalCenter in 0.3..0.7

        // Ukuran wajah minimal 30% dari lebar frame agar tidak terlalu jauh
        val faceWidthPercent = (bounds.width().toFloat() / imgWidth) * 100

        return isCentered && faceWidthPercent > 30
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
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}

class ScannerOverlayView(context: android.content.Context) : View(context) {
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        pathEffect = CornerPathEffect(20f) // Biar sudut kotak agak bulat
    }

    private val transparentPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#80000000") // Hitam transparan 50%
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Gambar latar belakang gelap di seluruh layar
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // 2. Tentukan ukuran kotak (misal 70% dari lebar layar)
        val boxWidth = width * 0.7f
        val boxHeight = boxWidth * 1.2f // Agak lonjong ke bawah untuk wajah

        val left = (width - boxWidth) / 2
        val top = (height - boxHeight) / 2
        val right = left + boxWidth
        val bottom = top + boxHeight
        val rect = RectF(left, top, right, bottom)

        // 3. Lubangi bagian tengah (kotak transparan)
        canvas.drawRect(rect, transparentPaint)

        // 4. Gambar garis pinggir kotak putih
        canvas.drawRect(rect, paint)
    }
}