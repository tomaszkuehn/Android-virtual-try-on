package com.virtualtryon.app.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * Processor for creating fallback image when no API key is provided
 * Overlays clothing image on person image at 50% scale and 50% opacity
 */
object FallbackProcessor {

    /**
     * Create fallback image by overlaying clothing on person
     * - Clothing is scaled to 50% of person image size
     * - Clothing has 50% opacity (alpha)
     */
    fun createFallbackImage(personBitmap: Bitmap, clothingBitmap: Bitmap): Bitmap {
        // Create result bitmap with same size as person image
        val result = Bitmap.createBitmap(personBitmap.width, personBitmap.height, personBitmap.config ?: Bitmap.Config.ARGB_8888)

        val canvas = Canvas(result)

        // Draw person image first
        canvas.drawBitmap(personBitmap, 0f, 0f, null)

        // Calculate 50% scale for clothing
        val scale = 0.5f
        val clothingWidth = (personBitmap.width * scale).toInt()
        val clothingHeight = (personBitmap.height * scale).toInt()

        // Resize clothing to 50% scale
        val resizedClothing = Bitmap.createScaledBitmap(clothingBitmap, clothingWidth, clothingHeight, true)

        // Calculate position to center the clothing on the person
        val left = (personBitmap.width - clothingWidth) / 2f
        val top = (personBitmap.height - clothingHeight) / 2f

        // Create paint with 50% opacity
        val paint = Paint().apply {
            alpha = 128 // 50% opacity (255 * 0.5 = 127.5 ≈ 128)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        // Draw clothing with 50% opacity at centered position
        canvas.drawBitmap(resizedClothing, left, top, paint)

        return result
    }
}
