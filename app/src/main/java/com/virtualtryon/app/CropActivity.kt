package com.virtualtryon.app

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.virtualtryon.app.utils.ImageUtils

class CropActivity : AppCompatActivity() {

    private lateinit var cropView: CropView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        val imageUri = intent.getParcelableExtra<Uri>("image_uri")
        if (imageUri == null) {
            Toast.makeText(this, "No image to crop", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        cropView = findViewById(R.id.cropView)
        
        try {
            val bitmap = ImageUtils.loadBitmapFromUri(this, imageUri)
            if (bitmap == null) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            cropView.setImageBitmap(bitmap)

            // Buttons
            findViewById<Button>(R.id.btnCrop).setOnClickListener {
                val croppedBitmap = cropView.getCroppedBitmap()
                if (croppedBitmap != null) {
                    saveAndReturn(croppedBitmap)
                } else {
                    Toast.makeText(this, "Error cropping image", Toast.LENGTH_SHORT).show()
                }
            }

            findViewById<Button>(R.id.btnCancel).setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveAndReturn(bitmap: Bitmap) {
        try {
            val file = java.io.File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            val fos = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.close()

            val resultUri = Uri.fromFile(file)
            val resultIntent = intent.putExtra("cropped_uri", resultUri)
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving cropped image", Toast.LENGTH_SHORT).show()
        }
    }
}
