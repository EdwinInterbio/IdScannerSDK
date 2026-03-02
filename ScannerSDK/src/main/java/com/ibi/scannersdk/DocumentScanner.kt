package com.ibi.scannersdk

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class DocumentScanner(
    private val activity: ComponentActivity
) {

    private var onResultCallback: ((String?) -> Unit)? = null
    private var onFaceResultCallback: ((String?) -> Unit)? = null

    // ✅ Launcher langsung register saat object dibuat
    private val scannerLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult =
                    GmsDocumentScanningResult.fromActivityResultIntent(result.data)

                val imageUri = scanResult?.pages?.get(0)?.imageUri

                if (imageUri != null) {
                    val base64 = ImageUtils.uriToBase64(activity, imageUri)
                    onResultCallback?.invoke(base64)
                } else {
                    onResultCallback?.invoke(null)
                }
            } else {
                onResultCallback?.invoke(null)
            }
        }

    // ✅ Face launcher juga langsung register
    private val faceLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val base64 = result.data?.getStringExtra("BASE64_FACE")
                onFaceResultCallback?.invoke(base64)
            } else {
                onFaceResultCallback?.invoke(null)
            }
        }

    fun startScan(callback: (String?) -> Unit) {
        this.onResultCallback = callback

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setPageLimit(1)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener {
                onResultCallback?.invoke(null)
            }
    }

    fun startFaceScan(callback: (String?) -> Unit) {
        this.onFaceResultCallback = callback
        val intent = Intent(activity, FaceScannerActivity::class.java)
        faceLauncher.launch(intent)
    }
}