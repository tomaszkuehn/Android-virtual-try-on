package com.virtualtryon.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rectPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#80000000")
    }

    private var cropRect = Rect()
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f

    fun setImageBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        cropRect = Rect(0, 0, bitmap.width, bitmap.height)
        invalidate()
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = bitmap ?: return null
        return try {
            Bitmap.createBitmap(bmp, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        } catch (e: Exception) {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return

        val viewWidth = width
        val viewHeight = height
        val bmpWidth = bmp.width
        val bmpHeight = bmp.height

        val scale = minOf(viewWidth.toFloat() / bmpWidth, viewHeight.toFloat() / bmpHeight)
        val displayWidth = (bmpWidth * scale).toInt()
        val displayHeight = (bmpHeight * scale).toInt()
        val offsetX = (viewWidth - displayWidth) / 2
        val offsetY = (viewHeight - displayHeight) / 2

        // Draw bitmap
        val srcRect = Rect(0, 0, bmpWidth, bmpHeight)
        val dstRect = Rect(offsetX, offsetY, offsetX + displayWidth, offsetY + displayHeight)
        canvas.drawBitmap(bmp, srcRect, dstRect, paint)

        // Draw crop overlay
        val cropLeft = offsetX + (cropRect.left * scale).toInt()
        val cropTop = offsetY + (cropRect.top * scale).toInt()
        val cropRight = offsetX + (cropRect.right * scale).toInt()
        val cropBottom = offsetY + (cropRect.bottom * scale).toInt()

        // Darken outside crop area
        canvas.drawRect(0f, 0f, width.toFloat(), cropTop.toFloat(), overlayPaint)
        canvas.drawRect(0f, cropBottom.toFloat(), width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawRect(0f, cropTop.toFloat(), cropLeft.toFloat(), cropBottom.toFloat(), overlayPaint)
        canvas.drawRect(cropRight.toFloat(), cropTop.toFloat(), width.toFloat(), cropBottom.toFloat(), overlayPaint)

        // Draw crop rectangle
        canvas.drawRect(cropLeft.toFloat(), cropTop.toFloat(), cropRight.toFloat(), cropBottom.toFloat(), rectPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return false

        val viewWidth = width
        val viewHeight = height
        val bmpWidth = bmp.width
        val bmpHeight = bmp.height

        val scale = minOf(viewWidth.toFloat() / bmpWidth, viewHeight.toFloat() / bmpHeight)
        val offsetX = (viewWidth - (bmpWidth * scale)) / 2
        val offsetY = (viewHeight - (bmpHeight * scale)) / 2

        val touchX = ((event.x - offsetX) / scale).toInt().coerceIn(0, bmpWidth)
        val touchY = ((event.y - offsetY) / scale).toInt().coerceIn(0, bmpHeight)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                dragStartX = touchX.toFloat()
                dragStartY = touchY.toFloat()
                cropRect.left = touchX
                cropRect.top = touchY
                cropRect.right = touchX
                cropRect.bottom = touchY
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    cropRect.right = touchX
                    cropRect.bottom = touchY
                    cropRect.sort()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
            }
        }
        return true
    }
}
