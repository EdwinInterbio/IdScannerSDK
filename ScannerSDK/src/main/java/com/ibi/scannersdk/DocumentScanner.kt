package com.ibi.scannersdk

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

class DocumentScanner(private val activity: ComponentActivity) : DefaultLifecycleObserver {

    private var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var faceLauncher: ActivityResultLauncher<Intent>? = null

    private var onResultCallback: ((String?) -> Unit)? = null
    private var onFaceResultCallback: ((String?) -> Unit)? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        // Register launcher DI SINI (aman secara lifecycle)
        scannerLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult =
                    com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
                        .fromActivityResultIntent(result.data)

                val imageUri = scanResult?.pages?.get(0)?.imageUri

                val base64 = imageUri?.let {
                    ImageUtils.uriToBase64(activity, it)
                }

                onResultCallback?.invoke(base64)
            } else {
                onResultCallback?.invoke(null)
            }
        }

        faceLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val base64 = result.data?.getStringExtra("BASE64_FACE")
                onFaceResultCallback?.invoke(base64)
            } else {
                onFaceResultCallback?.invoke(null)
            }
        }
    }

    fun startScan(callback: (String?) -> Unit) {
        this.onResultCallback = callback

        val launcher = scannerLauncher
            ?: throw IllegalStateException("Scanner not initialized yet")

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setPageLimit(1)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                onResultCallback?.invoke(null)
            }
    }

    fun startFaceScan(callback: (String?) -> Unit) {
        this.onFaceResultCallback = callback

        val launcher = faceLauncher
            ?: throw IllegalStateException("Face scanner not initialized yet")

        val intent = Intent(activity, FaceScannerActivity::class.java)
        launcher.launch(intent)
    }
}