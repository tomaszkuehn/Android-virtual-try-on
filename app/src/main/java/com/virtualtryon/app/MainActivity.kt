package com.virtualtryon.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.virtualtryon.app.api.ApiService
import com.virtualtryon.app.api.ImageEditRequest
import com.virtualtryon.app.api.InputData
import com.virtualtryon.app.api.Parameters
import com.virtualtryon.app.api.Message
import com.virtualtryon.app.api.ContentItem
import com.virtualtryon.app.api.ImageEditResponse
import com.virtualtryon.app.databinding.ActivityMainBinding
import com.virtualtryon.app.utils.FallbackProcessor
import com.virtualtryon.app.utils.ImageUtils
import coil.load
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var personImageUri: Uri? = null
    private var clothingImageUri: Uri? = null
    private var personBitmap: Bitmap? = null
    private var clothingBitmap: Bitmap? = null
    private var currentPhotoFile: File? = null

    // Activity Result Launchers
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val imageUri = data?.data
            if (imageUri != null) {
                when (currentImageType) {
                    ImageType.PERSON -> {
                        personImageUri = imageUri
                        loadPersonImage(imageUri)
                    }
                    ImageType.CLOTHING -> {
                        clothingImageUri = imageUri
                        // Just load the image, don't crop automatically
                        loadClothingImage(imageUri)
                    }
                }
            }
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val croppedUri = data?.getParcelableExtra<Uri>("cropped_uri")
            if (croppedUri != null) {
                clothingImageUri = croppedUri
                loadClothingImage(croppedUri)
            } else {
                // If crop returned null, use the original URI
                clothingImageUri?.let { uri ->
                    loadClothingImage(uri)
                }
            }
        } else {
            // If crop was cancelled, use the original URI
            clothingImageUri?.let { uri ->
                loadClothingImage(uri)
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: androidx.activity.result.ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            currentPhotoFile?.let { file ->
                val uri = Uri.fromFile(file)
                when (currentImageType) {
                    ImageType.PERSON -> {
                        personImageUri = uri
                        loadPersonImage(uri)
                    }
                    ImageType.CLOTHING -> {
                        clothingImageUri = uri
                        // Just load the image, don't crop automatically
                        loadClothingImage(uri)
                    }
                }
            }
        }
    }

    private var currentImageType: ImageType = ImageType.PERSON

    enum class ImageType {
        PERSON, CLOTHING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Person image buttons
        binding.btnSelectPersonGallery.setOnClickListener {
            currentImageType = ImageType.PERSON
            checkStoragePermissionAndOpenGallery()
        }

        binding.btnSelectPersonCamera.setOnClickListener {
            currentImageType = ImageType.PERSON
            checkCameraPermissionAndOpenCamera()
        }

        // Clothing image buttons
        binding.btnSelectClothingGallery.setOnClickListener {
            currentImageType = ImageType.CLOTHING
            checkStoragePermissionAndOpenGallery()
        }

        binding.btnSelectClothingCamera.setOnClickListener {
            currentImageType = ImageType.CLOTHING
            checkCameraPermissionAndOpenCamera()
        }

        // Crop clothing button - manually trigger crop
        binding.btnCropClothing.setOnClickListener {
            clothingImageUri?.let { uri ->
                cropClothingImage(uri)
            } ?: run {
                Toast.makeText(this, "Please select a clothing image first", Toast.LENGTH_SHORT).show()
            }
        }

        // Generate button
        binding.btnGenerate.setOnClickListener {
            generateTryOn()
        }

        // Result image click - show full screen
        binding.ivResult.setOnClickListener {
            showImageFullScreen()
        }
    }

    private fun checkStoragePermissionAndOpenGallery() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun openCamera() {
        val photoFile = try {
            createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(this, R.string.error_selecting_image, Toast.LENGTH_SHORT).show()
            null
        }

        photoFile?.let { file ->
            currentPhotoFile = file
            val photoURI = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            cameraLauncher.launch(intent)
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun loadPersonImage(uri: Uri) {
        try {
            personBitmap = ImageUtils.loadBitmapFromUri(this, uri)
            binding.ivPerson.load(uri)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_selecting_image, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadClothingImage(uri: Uri) {
        try {
            clothingBitmap = ImageUtils.loadBitmapFromUri(this, uri)
            binding.ivClothing.load(uri)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_selecting_image, Toast.LENGTH_SHORT).show()
        }
    }

    private fun cropClothingImage(uri: Uri) {
        try {
            // Use custom CropActivity instead of system crop
            val cropIntent = Intent(this, CropActivity::class.java)
            cropIntent.putExtra("image_uri", uri)
            cropLauncher.launch(cropIntent)
        } catch (e: Exception) {
            // If crop fails, just load the image normally
            loadClothingImage(uri)
            Toast.makeText(this, "Crop not available, using original image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateTryOn() {
        // Validate inputs
        if (personBitmap == null) {
            Toast.makeText(this, R.string.select_person_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (clothingBitmap == null) {
            Toast.makeText(this, R.string.select_clothing_first, Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = binding.etApiKey.text.toString().trim()

        // Show progress
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnGenerate.isEnabled = false

        if (apiKey.isEmpty()) {
            // No API key - use fallback
            Toast.makeText(this, R.string.no_api_key_warning, Toast.LENGTH_LONG).show()
            createFallbackImage()
        } else {
            // Hide API key section after entering valid key
            binding.apiKeySection.visibility = android.view.View.GONE
            // Call API
            callDashScopeApi(apiKey)
        }
    }

    private fun createFallbackImage() {
        try {
            val person = personBitmap
            val clothing = clothingBitmap

            if (person != null && clothing != null) {
                val result = FallbackProcessor.createFallbackImage(person, clothing)
                binding.ivResult.setImageBitmap(result)
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_processing_image, Toast.LENGTH_SHORT).show()
        } finally {
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnGenerate.isEnabled = true
        }
    }

    private fun showImageFullScreen() {
        val drawable = binding.ivResult.drawable
        if (drawable == null) {
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a full-screen dialog with the image
        val imageView = ImageView(this).apply {
            setImageDrawable(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        
        // Create layout with image and share button
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Add image view
        val imgParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        )
        layout.addView(imageView, imgParams)
        
        // Add share button at bottom
        val shareButton = android.widget.Button(this).apply {
            text = "Share Image"
            setOnClickListener {
                shareImage()
                dialog.dismiss()
            }
        }
        val btnParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layout.addView(shareButton, btnParams)
        
        dialog.setContentView(layout)
        
        // Ensure dialog fills the entire screen
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        dialog.show()

        // Close dialog when clicking on the image (but not on button)
        imageView.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun shareImage() {
        try {
            val drawable = binding.ivResult.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                val bitmap = drawable.bitmap
                
                // Save bitmap to cache
                val file = java.io.File(externalCacheDir, "shared_image_${System.currentTimeMillis()}.jpg")
                val fos = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.close()
                
                // Create share intent
                val uri = FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    file
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "image/jpeg"
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                startActivity(Intent.createChooser(shareIntent, "Share Image"))
            } else {
                Toast.makeText(this, "Cannot share this image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun callDashScopeApi(apiKey: String) {
        val person = personBitmap
        val clothing = clothingBitmap

        if (person == null || clothing == null) {
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnGenerate.isEnabled = true
            return
        }

        // Resize images for API
        val resizedPerson = ImageUtils.resizeBitmap(person, 1024)
        val resizedClothing = ImageUtils.resizeBitmap(clothing, 1024)

        // Create image data URLs
        val personDataUrl = ImageUtils.createImageDataUrl(resizedPerson)
        val clothingDataUrl = ImageUtils.createImageDataUrl(resizedClothing)

        // Create request using messages format for qwen-image-2.0
        val prompt = "Generate a realistic image showing the person from Image 1 wearing the dress, shoes, t-shirt, trousers, jewelerry items from Image 2. Preserve the pose of the person. Create a seamless virtual try-on result where the clothing fits naturally."
        
        val contentItems = listOf(
            ContentItem(image = personDataUrl),
            ContentItem(image = clothingDataUrl),
            ContentItem(text = prompt)
        )
        
        val request = ImageEditRequest(
            input = InputData(
                messages = listOf(Message(content = contentItems))
            ),
            parameters = Parameters(n = 1)
        )

        // Make API call
        val authHeader = ApiService.getAuthHeader(apiKey)
        ApiService.api.generateImage(authHeader, request).enqueue(object : Callback<com.virtualtryon.app.api.ImageEditResponse> {
            override fun onResponse(
                call: Call<com.virtualtryon.app.api.ImageEditResponse>,
                response: Response<com.virtualtryon.app.api.ImageEditResponse>
            ) {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnGenerate.isEnabled = true

                if (response.isSuccessful) {
                    val body = response.body()
                    val imageUrl = body?.output?.choices?.firstOrNull()?.message?.content?.firstOrNull()?.image
                    if (imageUrl != null) {
                        // Load image from URL
                        binding.ivResult.load(imageUrl)
                    } else {
                        Toast.makeText(this@MainActivity, R.string.error_api_call, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(this@MainActivity, "${getString(R.string.error_api_call)}: ${response.code()} - ${errorBody}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<com.virtualtryon.app.api.ImageEditResponse>, t: Throwable) {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnGenerate.isEnabled = true
                Toast.makeText(this@MainActivity, "${getString(R.string.error_api_call)}: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
