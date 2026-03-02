package com.ibi.scannersdk

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class DocumentScanner(
    private val activity: Activity,
    private val cardLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val faceLauncher: ActivityResultLauncher<Intent>
) {

    private var cardCallback: ((String?) -> Unit)? = null
    private var faceCallback: ((String?) -> Unit)? = null

    // =========================
    // CARD SCANNER
    // =========================
    fun launchCardScanner(onResult: (String?) -> Unit) {
        cardCallback = onResult

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setPageLimit(1)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                cardLauncher.launch(request)
            }
            .addOnFailureListener {
                cardCallback?.invoke(null)
            }
    }

    fun handleCardResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            cardCallback?.invoke(null)
            return
        }

        val result =
            GmsDocumentScanningResult.fromActivityResultIntent(data)

        val uri = result?.pages?.firstOrNull()?.imageUri
        cardCallback?.invoke(uri?.toString())
    }

    // =========================
    // FACE SCANNER
    // =========================
    fun startFaceScan(onResult: (String?) -> Unit) {
        faceCallback = onResult

        val intent = Intent(activity, FaceScannerActivity::class.java)
        faceLauncher.launch(intent)
    }

    fun handleFaceResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            faceCallback?.invoke(null)
            return
        }

        val base64 = data?.getStringExtra("BASE64_FACE")
        faceCallback?.invoke(base64)
    }
}