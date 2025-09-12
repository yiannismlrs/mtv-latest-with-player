# MTV Streaming App - Android

This is a fully self-contained Android application for the MTV streaming platform. The app includes both frontend and backend functionality embedded within the Android project.

## Features

- 🎬 Browse and search movies and TV shows
- 📺 Stream content via SPlayer integration
- 📋 Personal watchlist management
- 🔥 Trending and popular content
- 📱 Native Android experience with WebView
- 🌐 Offline-capable with local storage

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── assets/www/          # Web application files
│   │   │   ├── index.html       # Main HTML file
│   │   │   ├── js/app.js        # JavaScript application logic
│   │   │   └── manifest.json    # PWA manifest
│   │   ├── java/                # Android Java code
│   │   └── res/                 # Android resources
│   └── build.gradle             # App-level build configuration
├── build.gradle                 # Project-level build configuration
└── README.md                    # This file
```

## Building the App

### Prerequisites
- Android Studio Arctic Fox or later
- Java JDK 17
- Android SDK API level 34

### Build Steps

1. **Open in Android Studio:**
   ```bash
   # Open the android folder in Android Studio
   ```

2. **Sync Project:**
   - Click "Sync Now" when prompted
   - Wait for Gradle sync to complete

3. **Build APK:**
   ```bash
   # Via Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s)
   # Or via command line:
   cd android
   ./gradlew assembleDebug
   ```

4. **Install APK:**
   ```bash
   # The APK will be generated at:
   # android/app/build/outputs/apk/debug/app-debug.apk
   
   # Install via ADB:
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Development

### Web Content
The web application is located in `android/app/src/main/assets/www/`. This includes:
- HTML, CSS, and JavaScript files
- All frontend logic and UI
- API integration with TMDB
- Local storage for watchlist

### Android Integration
- `MainActivity.java` handles WebView configuration and SPlayer integration
- WebView settings optimized for streaming content
- Custom URL scheme handling for external app integration

### API Configuration
The app uses TMDB API for movie and TV show data:
- API key is embedded in the JavaScript code
- All API calls are made directly from the WebView
- No separate backend server required

## Features

### Content Discovery
- Browse trending movies and TV shows
- Search functionality across all content
- Popular content recommendations
- Detailed content information

### Streaming Integration
- SPlayer app integration for video playback
- Automatic URL generation for streaming sources
- Fallback handling when SPlayer is not installed

### Watchlist Management
- Add/remove content from personal watchlist
- Local storage persistence
- Cross-session watchlist retention

## Deployment

### Debug Build
```bash
cd android
./gradlew assembleDebug
```

### Release Build
```bash
cd android
./gradlew assembleRelease
```

### GitHub Actions
The project includes GitHub Actions workflow for automated APK building:
- Triggers on push to main branch
- Builds and uploads APK artifacts
- Creates GitHub releases automatically

## Troubleshooting

### Common Issues

1. **Gradle Sync Failed:**
   - Ensure you have the correct Android SDK installed
   - Check internet connection for dependency downloads

2. **WebView Not Loading:**
   - Verify assets are properly placed in `www` folder
   - Check Android permissions in manifest

3. **SPlayer Integration:**
   - Ensure SPlayer app is installed on device
   - Check custom URL scheme handling in MainActivity

### Debug Mode
Enable WebView debugging in Chrome DevTools:
1. Enable "USB Debugging" on Android device
2. Open Chrome and navigate to `chrome://inspect`
3. Select your device and WebView to debug

## License

This project is licensed under the MIT License.