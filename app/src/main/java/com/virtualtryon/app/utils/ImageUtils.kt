package com.virtualtryon.app.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Utility class for image processing
 */
object ImageUtils {

    /**
     * Convert Bitmap to Base64 string
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    /**
     * Load Bitmap from URI
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val contentResolver: ContentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resize bitmap to max dimension while keeping aspect ratio
     */
    fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Create image data URL for API (data:image/jpeg;base64,...)
     */
    fun createImageDataUrl(bitmap: Bitmap): String {
        val base64 = bitmapToBase64(bitmap)
        return "data:image/jpeg;base64,$base64"
    }
}
