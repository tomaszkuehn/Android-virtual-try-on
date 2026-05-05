# Virtual Try-On Android App

An Android application for virtual try-on of clothes using artificial intelligence.

## Project Description

Virtual Try-On is a mobile application that allows users to virtually try on clothes. The app takes a photo of a person and a photo of clothing, then uses the DashScope API to generate a realistic image showing the person wearing the selected clothing item.

## Features

### Core Features:
- **Select person photo** - from gallery or camera
- **Select clothing photo** - from gallery or camera
- **Crop images** - freely crop clothing before generation
- **Generate result** - use AI to overlay clothing on the person
- **Full-screen preview** - click on result to view in full screen
- **Offline mode** - fallback without API key (simple image overlay)

### Image Editing:
- Images display in full (fitCenter) while maintaining aspect ratio
- Free-form cropping of clothing images
- Automatic image scaling to optimal sizes

## System Requirements

- Android 7.0 (API 24) or higher
- Permissions: Camera, Storage, Internet
- For full functionality: DashScope API key

## Installation and Configuration

### 1. Clone the repository
```bash
git clone [REPOSITORY_URL]
cd virtual_try_on_android
```

### 2. Open in Android Studio
- Open Android Studio
- Select "Open an existing Android Studio project"
- Choose the `virtual_try_on_android` folder

### 3. API Key Configuration (Optional)
The app works in two modes:
- **With API key** - uses DashScope AI for realistic generation
- **Without API key** - fallback mode (simple image overlay)

To use AI:
1. Get an API key from Alibaba cloud https://www.alibabacloud.com/en
2. Enter the key in the "Enter DashScope API Key" field in the app
3. After entering the key, the field disappears (remembered in current session)

## Usage

### Step by step:

1. **Select person photo**
   - Click "Gallery" or "Camera" in the "Select Person Photo" section
   - Photo will be displayed in the preview

2. **Select clothing photo**
   - Click "Gallery" or "Camera" in the "Select Clothing Photo" section
   - Photo will be displayed in the preview

3. **Crop clothing (optional)**
   - Click the "Crop Image" button under the clothing photo
   - Drag your finger to select the area to crop
   - Click "Crop" to confirm or "Cancel" to cancel

4. **Generate result**
   - (Optional) Enter DashScope API key
   - Click "Generate Try-On"
   - Wait for the result to generate (progress bar)

5. **View result**
   - Click on the generated image to view it in full screen
   - Click again to return to the main screen

## Project Structure

```
app/
├── src/main/
│   ├── java/com/virtualtryon/app/
│   │   ├── MainActivity.kt          # Main activity
│   │   ├── CropActivity.kt          # Crop activity
│   │   ├── CropView.kt              # Custom crop view
│   │   ├── api/
│   │   │   ├── ApiService.kt       # Retrofit configuration
│   │   │   └── DashScopeApi.kt    # API interface
│   │   └── utils/
│   │       ├── ImageUtils.kt        # Image utilities
│   │       └── FallbackProcessor.kt # Fallback without API
│   ├── res/layout/
│   │   ├── activity_main.xml       # Main layout
│   │   └── activity_crop.xml      # Crop layout
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro
```

## Technologies

- **Language:** Kotlin
- **UI:** View Binding, XML Layouts
- **Networking:** Retrofit2, OkHttp3
- **Image Loading:** Coil
- **API:** DashScope (qwen-image-2.0)
- **Build System:** Gradle (Kotlin DSL)

## DashScope API

The app uses the DashScope API for image generation. Request format:

```kotlin
// Message with two images and a prompt
val contentItems = listOf(
    ContentItem(image = personDataUrl),    // Person photo
    ContentItem(image = clothingDataUrl),   // Clothing photo
    ContentItem(text = prompt)             // Instructions for AI
)
```

## Fallback Mode (Without API)

If you don't provide an API key, the app uses `FallbackProcessor` which:
- Simply overlays clothing on the person's photo
- Doesn't require internet connection
- Provides basic effect (less realistic than AI)

## Permissions

The app requires the following permissions:
- `CAMERA` - taking photos
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` - selecting photos from gallery
- `INTERNET` - connecting to API

## Known Limitations

- System crop (ACTION_CROP) doesn't work on newer devices (custom solution implemented)
- API generation may take from a few to several seconds
- Internet connection required for AI features

## Future Development

### Possible improvements:
- [ ] Save generated images to gallery
- [ ] History of generated images
- [ ] More image editing options (brightness, contrast, etc.)
- [ ] Support for different clothing types (pants, shoes, accessories)
- [ ] Share results on social media

## License

[ADD YOUR LICENSE]

## Author

[ADD YOUR NAME]

## Acknowledgments

- DashScope for the image generation API
- Open-source libraries used in the project
