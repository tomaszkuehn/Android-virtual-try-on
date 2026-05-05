package com.virtualtryon.app

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.DragEvent
import android.view.View
import android.view.View.DragShadowBuilder
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var personImageUri: Uri? = null
    private var clothingImageUri: Uri? = null
    private var personBitmap: Bitmap? = null
    private var clothingBitmap: Bitmap? = null
    private var currentPhotoFile: File? = null

    // Wardrobe storage
    private val WARDROBE_SIZE = 5
    private lateinit var wardrobeSlots: Array<ImageView>
    private lateinit var wardrobeUris: Array<Uri?>
    private lateinit var wardrobeHashes: Array<Long>
    private lateinit var prefs: SharedPreferences
    private val WARDROBE_PREFS = "wardrobe_prefs"
    private val WARDROBE_URI_PREFIX = "wardrobe_uri_"
    private val WARDROBE_HASH_PREFIX = "wardrobe_hash_"

    enum class ImageType {
        PERSON, CLOTHING
    }

    private var currentImageType: ImageType = ImageType.PERSON

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
                clothingImageUri?.let { uri ->
                    loadClothingImage(uri)
                }
            }
        } else {
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
                // Use FileProvider to create a content URI instead of file URI
                val uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    file
                )
                when (currentImageType) {
                    ImageType.PERSON -> {
                        personImageUri = uri
                        loadPersonImage(uri)
                    }
                    ImageType.CLOTHING -> {
                        clothingImageUri = uri
                        loadClothingImage(uri)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initWardrobe()
        setupClickListeners()
        setupDragAndDrop()
    }

    private fun initWardrobe() {
        prefs = getSharedPreferences(WARDROBE_PREFS, MODE_PRIVATE)

        wardrobeSlots = arrayOf(
            binding.wardrobeSlot1,
            binding.wardrobeSlot2,
            binding.wardrobeSlot3,
            binding.wardrobeSlot4,
            binding.wardrobeSlot5
        )

        @Suppress("UNCHECKED_CAST")
        wardrobeUris = arrayOfNulls<Uri>(WARDROBE_SIZE) as Array<Uri?>
        wardrobeHashes = Array(WARDROBE_SIZE) { 0L }

        for (i in 0 until WARDROBE_SIZE) {
            val uriString = prefs.getString(WARDROBE_URI_PREFIX + i, null)
            val uri = uriString?.let { Uri.parse(it) }
            val hash = prefs.getLong(WARDROBE_HASH_PREFIX + i, 0L)

            if (uri == null || hash == 0L) {
                clearWardrobeSlot(i)
                continue
            }

            // Check if URI is valid and file exists
            if (!isUriValid(uri)) {
                android.util.Log.e("Wardrobe", "Invalid URI for slot $i: $uri")
                clearWardrobeSlot(i)
                continue
            }

            wardrobeUris[i] = uri
            wardrobeHashes[i] = hash

            // Load image with error handling
            val index = i
            wardrobeSlots[i].load(uri) {
                placeholder(android.R.color.darker_gray)
                error(android.R.color.holo_red_light)
                listener(
                    onError = { _, error ->
                        android.util.Log.e("Wardrobe", "Failed to load image in slot $index: ${error.throwable.message}")
                        // Clear invalid slot on load error
                        clearWardrobeSlot(index)
                    },
                    onSuccess = { _, _ ->
                        android.util.Log.d("Wardrobe", "Successfully loaded image in slot $index")
                    }
                )
            }
        }
    }

    private fun isUriValid(uri: Uri): Boolean {
        return try {
            // Try to open the stream and check if we can read image bounds
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                android.util.Log.e("Wardrobe", "InputStream is null for URI: $uri")
                return false
            }
            // Try to decode just the bounds to verify it's a valid image
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            val isValid = options.outWidth > 0 && options.outHeight > 0
            if (!isValid) {
                android.util.Log.e("Wardrobe", "Invalid image dimensions for URI: $uri")
            }
            isValid
        } catch (e: Exception) {
            android.util.Log.e("Wardrobe", "Exception validating URI $uri: ${e.message}")
            false
        }
    }

    private fun clearWardrobeSlot(index: Int) {
        // Delete the physical file if it's in our app storage
        wardrobeUris[index]?.let { oldUri ->
            if (oldUri.toString().contains("wardrobe_images")) {
                try {
                    val path = oldUri.path
                    if (path != null) {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        wardrobeUris[index] = null
        wardrobeHashes[index] = 0L
        wardrobeSlots[index].setImageDrawable(null)
        val editor = prefs.edit()
        editor.remove(WARDROBE_URI_PREFIX + index)
        editor.remove(WARDROBE_HASH_PREFIX + index)
        editor.apply()
    }

    private fun copyImageToAppStorage(uri: Uri): Uri? {
        return try {
            // Create wardrobe directory if it doesn't exist
            val storageDir = getExternalFilesDir("wardrobe")
            if (storageDir == null) {
                android.util.Log.e("Wardrobe", "Failed to get external files dir")
                return null
            }
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val destFile = java.io.File(storageDir, "wardrobe_${timestamp}.jpg")
            
            // Copy the image
            val inputStream = contentResolver.openInputStream(uri)
            val outputStream = java.io.FileOutputStream(destFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            
            // Return content URI via FileProvider
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                destFile
            )
        } catch (e: Exception) {
            android.util.Log.e("Wardrobe", "Failed to copy image: ${e.message}")
            null
        }
    }

    private fun setupDragAndDrop() {
        binding.ivClothing.setOnLongClickListener { view ->
            val uri = clothingImageUri
            if (uri != null) {
                val clipData = ClipData.newUri(
                    this@MainActivity.contentResolver,
                    "clothing",
                    uri
                )
                val dragShadow = DragShadowBuilder(view)
                // Add DRAG_FLAG_GLOBAL_URI_READ to grant permission to read the URI
                view.startDragAndDrop(clipData, dragShadow, null, 
                    View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
                true
            } else {
                Toast.makeText(this, "No clothing image to drag", Toast.LENGTH_SHORT).show()
                false
            }
        }

        for (i in 0 until WARDROBE_SIZE) {
            val slot = wardrobeSlots[i]
            val index = i
            slot.setOnDragListener { view, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        // Return true to indicate we're interested in this drag operation
                        true
                    }
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        view.setBackgroundColor(Color.GREEN)
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        view.setBackgroundColor(Color.WHITE)
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        view.setBackgroundColor(Color.WHITE)
                        val clipData = event.clipData
                        if (clipData != null && clipData.itemCount > 0) {
                            val uri = clipData.getItemAt(0).uri
                            Toast.makeText(this@MainActivity, "Drop detected at slot ${index + 1}, URI: $uri", Toast.LENGTH_LONG).show()
                            addToWardrobe(index, uri)
                        } else {
                            Toast.makeText(this@MainActivity, "No data in drop event", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        view.setBackgroundColor(Color.WHITE)
                        true
                    }
                    else -> false
                }
            }

            slot.setOnClickListener {
                wardrobeUris[index]?.let { uri ->
                    clothingImageUri = uri
                    loadClothingImage(uri)
                    Toast.makeText(this, "Loaded image from wardrobe slot ${index + 1}", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(this, "Wardrobe slot ${index + 1} is empty", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addToWardrobe(slotIndex: Int, uri: Uri) {
        Toast.makeText(this, "Adding image to slot ${slotIndex + 1}", Toast.LENGTH_SHORT).show()
        
        // Copy image to app storage for persistence
        val persistentUri = copyImageToAppStorage(uri)
        if (persistentUri == null) {
            Toast.makeText(this, "Failed to save image to wardrobe", Toast.LENGTH_SHORT).show()
            return
        }
        
        val hash = calculateImageHash(persistentUri)
        Toast.makeText(this, "Image hash: $hash", Toast.LENGTH_SHORT).show()

        // Check for duplicates, but clear invalid entries
        for (i in 0 until WARDROBE_SIZE) {
            if (i != slotIndex && wardrobeHashes[i] == hash && hash != 0L) {
                // Check if the existing duplicate is still valid
                val existingUri = wardrobeUris[i]
                if (existingUri == null || !isUriValid(existingUri)) {
                    // Clear invalid duplicate entry
                    clearWardrobeSlot(i)
                    continue
                }
                // Delete the copy we just made since it's a duplicate
                try {
                    val path = persistentUri.path
                    if (path != null) {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    }
                } catch (e: Exception) { }
                Toast.makeText(this, "Image duplicate", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Clear old image in this slot if replacing
        if (wardrobeUris[slotIndex] != null) {
            clearWardrobeSlot(slotIndex)
        }

        wardrobeUris[slotIndex] = persistentUri
        wardrobeHashes[slotIndex] = hash

        // Load image into the slot using Coil with proper error handling
        try {
            val context = this
            wardrobeSlots[slotIndex].load(persistentUri) {
                placeholder(android.R.color.darker_gray)
                error(android.R.color.holo_red_light)
                listener(
                    onError = { _, error ->
                        Toast.makeText(context, "Coil error: ${error.throwable.message}", Toast.LENGTH_LONG).show()
                        clearWardrobeSlot(slotIndex)
                    },
                    onSuccess = { _, _ ->
                        Toast.makeText(context, "Image loaded successfully", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            clearWardrobeSlot(slotIndex)
        }

        // Save to SharedPreferences
        val editor = prefs.edit()
        editor.putString(WARDROBE_URI_PREFIX + slotIndex, persistentUri.toString())
        editor.putLong(WARDROBE_HASH_PREFIX + slotIndex, hash)
        editor.apply()

        Toast.makeText(
            this,
            "Image stored in wardrobe slot ${slotIndex + 1}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun calculateImageHash(uri: Uri): Long {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val smallBitmap = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
                val crc = CRC32()
                for (y in 0 until smallBitmap.height) {
                    for (x in 0 until smallBitmap.width) {
                        val pixel = smallBitmap.getPixel(x, y)
                        crc.update(pixel)
                    }
                }
                crc.value
            } else {
                0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectPersonGallery.setOnClickListener {
            currentImageType = ImageType.PERSON
            checkStoragePermissionAndOpenGallery()
        }

        binding.btnSelectPersonCamera.setOnClickListener {
            currentImageType = ImageType.PERSON
            checkCameraPermissionAndOpenCamera()
        }

        binding.btnSelectClothingGallery.setOnClickListener {
            currentImageType = ImageType.CLOTHING
            checkStoragePermissionAndOpenGallery()
        }

        binding.btnSelectClothingCamera.setOnClickListener {
            currentImageType = ImageType.CLOTHING
            checkCameraPermissionAndOpenCamera()
        }

        binding.btnCropClothing.setOnClickListener {
            clothingImageUri?.let { uri ->
                cropClothingImage(uri)
            } ?: run {
                Toast.makeText(this, "Please select a clothing image first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGenerate.setOnClickListener {
            generateTryOn()
        }

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
                "${packageName}.fileprovider",
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
            val cropIntent = Intent(this, CropActivity::class.java)
            cropIntent.putExtra("image_uri", uri)
            cropLauncher.launch(cropIntent)
        } catch (e: Exception) {
            loadClothingImage(uri)
            Toast.makeText(this, "Crop not available, using original image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateTryOn() {
        if (personBitmap == null) {
            Toast.makeText(this, R.string.select_person_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (clothingBitmap == null) {
            Toast.makeText(this, R.string.select_clothing_first, Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = binding.etApiKey.text.toString().trim()

        binding.progressBar.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false

        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.no_api_key_warning, Toast.LENGTH_LONG).show()
            createFallbackImage()
        } else {
            binding.apiKeySection.visibility = View.GONE
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
            binding.progressBar.visibility = View.GONE
            binding.btnGenerate.isEnabled = true
        }
    }

    private fun showImageFullScreen() {
        val drawable = binding.ivResult.drawable
        if (drawable == null) {
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show()
            return
        }

        val imageView = ImageView(this).apply {
            setImageDrawable(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val imgParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        )
        layout.addView(imageView, imgParams)

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
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        dialog.show()
        imageView.setOnClickListener { dialog.dismiss() }
    }

    private fun shareImage() {
        try {
            val drawable = binding.ivResult.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                val bitmap = drawable.bitmap
                val file = java.io.File(externalCacheDir, "shared_image_${System.currentTimeMillis()}.jpg")
                val fos = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.close()

                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
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
            binding.progressBar.visibility = View.GONE
            binding.btnGenerate.isEnabled = true
            return
        }

        val resizedPerson = ImageUtils.resizeBitmap(person, 1024)
        val resizedClothing = ImageUtils.resizeBitmap(clothing, 1024)

        val personDataUrl = ImageUtils.createImageDataUrl(resizedPerson)
        val clothingDataUrl = ImageUtils.createImageDataUrl(resizedClothing)

        val prompt = "Generate a realistic image showing the person from Image 1 wearing the dress, shoes, t-shirt, trousers, jewellery items from Image 2. Preserve the pose of the person. Create a seamless virtual try-on result where the clothing fits naturally."

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

        val authHeader = ApiService.getAuthHeader(apiKey)
        ApiService.api.generateImage(authHeader, request).enqueue(object : Callback<ImageEditResponse> {
            override fun onResponse(
                call: Call<ImageEditResponse>,
                response: Response<ImageEditResponse>
            ) {
                binding.progressBar.visibility = View.GONE
                binding.btnGenerate.isEnabled = true

                if (response.isSuccessful) {
                    val body = response.body()
                    val imageUrl = body?.output?.choices?.firstOrNull()?.message?.content?.firstOrNull()?.image
                    if (imageUrl != null) {
                        binding.ivResult.load(imageUrl)
                    } else {
                        Toast.makeText(this@MainActivity, R.string.error_api_call, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(this@MainActivity, "${getString(R.string.error_api_call)}: ${response.code()} - ${errorBody}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<ImageEditResponse>, t: Throwable) {
                binding.progressBar.visibility = View.GONE
                binding.btnGenerate.isEnabled = true
                Toast.makeText(this@MainActivity, "${getString(R.string.error_api_call)}: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}