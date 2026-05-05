package com.virtualtryon.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
            // Save to external files directory for persistence (not cache which can be cleared)
            val storageDir = getExternalFilesDir("cropped")
            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs()
            }
            val file = java.io.File(storageDir, "cropped_${System.currentTimeMillis()}.jpg")
            val fos = java.io.FileOutputStream(file)
            bitmap.compress(CompressFormat.JPEG, 90, fos)
            fos.close()

            // Use FileProvider to create a content URI instead of file URI
            val resultUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val resultIntent = Intent()
            resultIntent.putExtra("cropped_uri", resultUri)
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving cropped image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
