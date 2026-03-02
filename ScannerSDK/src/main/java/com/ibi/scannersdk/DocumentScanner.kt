package com.ibi.scannersdk

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import android.net.Uri

class DocumentScanner(
    private val activity: ComponentActivity
) {

    private var cardCallback: ((String?) -> Unit)? = null
    private var faceCallback: ((String?) -> Unit)? = null

    // =========================
    // CARD RESULT LAUNCHER
    // =========================
    private val cardLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->

            if (result.resultCode != Activity.RESULT_OK) {
                cardCallback?.invoke(null)
                return@registerForActivityResult
            }

            val scanResult =
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            val uri = scanResult?.pages?.firstOrNull()?.imageUri

            if (uri != null) {
                val base64 = ImageUtils.uriToBase64(activity, uri)
                cardCallback?.invoke(base64)
            } else {
                cardCallback?.invoke(null)
            }
        }

    // =========================
    // FACE RESULT LAUNCHER
    // =========================
    private val faceLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != Activity.RESULT_OK) {
                faceCallback?.invoke(null)
                return@registerForActivityResult
            }

            val base64 = result.data?.getStringExtra("BASE64_FACE")
            faceCallback?.invoke(base64)
        }

    // =========================
    // START CARD SCAN
    // =========================
    fun startCardScan(onResult: (String?) -> Unit) {
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

    // =========================
    // START FACE SCAN
    // =========================
    fun startFaceScan(onResult: (String?) -> Unit) {
        faceCallback = onResult

        val intent = Intent(activity, FaceScannerActivity::class.java)
        faceLauncher.launch(intent)
    }
}