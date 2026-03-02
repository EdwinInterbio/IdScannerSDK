package com.ibi.scannersdk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

internal object ImageUtils {
    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // 1. Resize: Batasi lebar/tinggi maksimal 1024px (untuk kartu agar teks tetap terbaca)
            val resizedBitmap = getResizedBitmap(originalBitmap, 1024)

            val outputStream = ByteArrayOutputStream()

            // 2. Compress: Gunakan kualitas 70 agar size kecil tapi teks KTP tetap tajam
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)

            val byteArray = outputStream.toByteArray()

            // 3. Encode: Gunakan NO_WRAP agar tidak ada karakter baris baru (\n)
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.width
        var height = image.height

        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            if (width > maxSize) {
                width = maxSize
                height = (width / bitmapRatio).toInt()
            }
        } else {
            if (height > maxSize) {
                height = maxSize
                width = (height * bitmapRatio).toInt()
            }
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }
}
