# MTV Streaming App - Android

This is a fully self-contained Android application for the MTV streaming platform. The entire application is contained within this android folder, with no external dependencies on separate server or client folders.

## Features

- 🎬 Browse and search movies and TV shows
- 📺 Stream content via SPlayer integration
- 📋 Personal watchlist management
- 🔥 Trending and popular content
- 📱 Native Android experience with WebView
- 🌐 Offline-capable with local storage
- 🔍 Search history and recently viewed content
- ⚙️ User preferences and settings

## Project Structure

```
android/
├── package.json                 # Node.js dependencies and scripts
├── capacitor.config.json        # Capacitor configuration
├── app/
│   ├── src/main/
│   │   ├── assets/www/          # Web application files
│   │   │   ├── index.html       # Main HTML file
│   │   │   ├── css/styles.css   # Application styles
│   │   │   ├── js/
│   │   │   │   ├── config.js    # Configuration settings
│   │   │   │   ├── api.js       # API service layer
│   │   │   │   ├── storage.js   # Local storage management
│   │   │   │   └── app.js       # Main application logic
│   │   │   └── manifest.json    # PWA manifest
│   │   ├── java/                # Android Java code
│   │   └── res/                 # Android resources
│   └── build.gradle             # App-level build configuration
├── build.gradle                 # Project-level build configuration
├── gradlew                      # Gradle wrapper script
├── gradlew.bat                  # Gradle wrapper script (Windows)
└── README.md                    # This file
```

## Building the App

### Prerequisites
- Android Studio Arctic Fox or later
- Java JDK 17
- Android SDK API level 34
- Node.js 18+ (optional, for package management)

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
   ./gradlew assembleDebug
   ```

4. **Install APK:**
   ```bash
   # The APK will be generated at:
   # app/build/outputs/apk/debug/app-debug.apk
   
   # Install via ADB:
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Using NPM Scripts (Optional)

If you have Node.js installed, you can use the provided npm scripts:

```bash
# Build debug APK
npm run build

# Build release APK
npm run build:release

# Clean build
npm run clean

# Install debug APK to connected device
npm run install:debug
```

## Development

### Web Content
The web application is located in `app/src/main/assets/www/` and includes:

- **HTML**: Main application structure
- **CSS**: Responsive design with MTV branding
- **JavaScript Modules**:
  - `config.js`: Application configuration
  - `api.js`: TMDB API integration with caching
  - `storage.js`: Local storage management
  - `app.js`: Main application logic and UI

### Architecture

The application follows a modular architecture:

1. **Configuration Layer** (`config.js`): Centralized settings
2. **Storage Layer** (`storage.js`): Local data persistence
3. **API Layer** (`api.js`): External API integration with caching
4. **Application Layer** (`app.js`): UI logic and user interactions

### Android Integration
- `MainActivity.java` handles WebView configuration and SPlayer integration
- WebView settings optimized for streaming content
- Custom URL scheme handling for external app integration

### API Configuration
The app uses TMDB API for movie and TV show data:
- API key is configured in `config.js`
- All API calls are made directly from the WebView
- Built-in caching system for improved performance
- No separate backend server required

## Features

### Content Discovery
- Browse trending movies and TV shows
- Search functionality across all content
- Popular content recommendations
- Detailed content information
- Genre-based filtering
- Recently viewed content

### Streaming Integration
- SPlayer app integration for video playback
- Automatic URL generation for streaming sources
- Fallback handling when SPlayer is not installed

### Watchlist Management
- Add/remove content from personal watchlist
- Local storage persistence
- Cross-session watchlist retention

### User Experience
- Search history tracking
- Recently viewed content
- User preferences storage
- Responsive design for all screen sizes
- Offline capability with cached data

## Deployment

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### GitHub Actions
The project includes GitHub Actions workflow for automated APK building:
- Triggers on push to main branch
- Builds and uploads APK artifacts
- Creates GitHub releases automatically

## Configuration

### API Configuration
Edit `app/src/main/assets/www/js/config.js` to modify:
- TMDB API settings
- Streaming service URLs
- Cache configuration
- App metadata

### Android Configuration
Edit the following files for Android-specific settings:
- `app/src/main/AndroidManifest.xml`: Permissions and app metadata
- `app/build.gradle`: Build configuration and dependencies
- `capacitor.config.json`: Capacitor plugin configuration

## Troubleshooting

### Common Issues

1. **Gradle Sync Failed:**
   - Ensure you have the correct Android SDK installed
   - Check internet connection for dependency downloads
   - Try cleaning the project: `./gradlew clean`

2. **WebView Not Loading:**
   - Verify assets are properly placed in `app/src/main/assets/www` folder
   - Check Android permissions in manifest
   - Enable WebView debugging for troubleshooting

3. **SPlayer Integration:**
   - Ensure SPlayer app is installed on device
   - Check custom URL scheme handling in MainActivity

4. **API Issues:**
   - Verify TMDB API key in `config.js`
   - Check network connectivity
   - Review API rate limits

### Debug Mode
Enable WebView debugging in Chrome DevTools:
1. Enable "USB Debugging" on Android device
2. Open Chrome and navigate to `chrome://inspect`
3. Select your device and WebView to debug

### Performance Optimization
- The app includes built-in caching for API responses
- Images are loaded lazily for better performance
- Local storage is used for offline functionality

## Self-Contained Architecture

This project is designed to be completely self-contained within the android folder:

- **No External Dependencies**: All code is contained within this folder
- **No Separate Server**: The app works entirely client-side
- **No Build Process**: Web assets are served directly from the assets folder
- **Embedded Configuration**: All settings are included in the project files

The entire application can be built, deployed, and distributed using only the contents of this android folder.

## License

This project is licensed under the MIT License.