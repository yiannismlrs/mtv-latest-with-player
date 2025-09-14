# MTV Streaming Platform - Self-Contained Android App

A fully self-contained Android streaming application for movies and TV shows, built with modern web technologies and packaged as a native Android app.

## 🚀 Features

- 🎬 **Content Discovery**: Browse trending and popular movies & TV shows
- 🔍 **Smart Search**: Search across all content with history tracking
- 📋 **Personal Watchlist**: Save and manage your favorite content
- 📱 **Native Experience**: Optimized Android WebView with native integration
- 🌐 **Offline Capable**: Local storage for watchlist and preferences
- 🎯 **SPlayer Integration**: Seamless video playback with external player
- 📊 **Recently Viewed**: Track your viewing history
- ⚙️ **User Preferences**: Customizable settings and preferences

## 📁 Project Structure

This project is **completely self-contained** within the `android/` folder. No separate server or client folders are required.

```
android/                          # Complete application
├── package.json                  # Build scripts and metadata
├── capacitor.config.json         # Capacitor configuration
├── app/
│   ├── src/main/
│   │   ├── assets/www/          # Web application
│   │   │   ├── index.html       # Main HTML
│   │   │   ├── css/styles.css   # Application styles
│   │   │   ├── js/
│   │   │   │   ├── config.js    # Configuration
│   │   │   │   ├── api.js       # API service
│   │   │   │   ├── storage.js   # Storage service
│   │   │   │   └── app.js       # Main application
│   │   │   └── manifest.json    # PWA manifest
│   │   ├── java/                # Android code
│   │   └── res/                 # Android resources
│   └── build.gradle             # App build config
├── build.gradle                 # Project build config
└── README.md                    # Documentation
```

## 🛠️ Building the App

### Prerequisites

- **Android Studio** Arctic Fox or later
- **Java JDK 17**
- **Android SDK** API level 34
- **Node.js 18+** (optional, for npm scripts)

### Quick Start

1. **Clone and Navigate**
   ```bash
   git clone <repository-url>
   cd android
   ```

2. **Build APK**
   ```bash
   # Using Gradle directly
   ./gradlew assembleDebug
   
   # Or using npm scripts (if Node.js is installed)
   npm run build
   ```

3. **Install on Device**
   ```bash
   # Install debug APK
   adb install app/build/outputs/apk/debug/app-debug.apk
   
   # Or using npm script
   npm run install:debug
   ```

### Available NPM Scripts

```bash
npm run build          # Build debug APK
npm run build:release  # Build release APK
npm run clean          # Clean build files
npm run install:debug  # Install debug APK to device
```

## 🏗️ Architecture

### Self-Contained Design

The application is designed to be completely independent:

- **No External Server**: All functionality runs client-side
- **No Build Process**: Web assets are served directly from Android assets
- **Embedded APIs**: TMDB integration with built-in caching
- **Local Storage**: All user data stored locally on device

### Modular JavaScript Architecture

1. **Configuration Layer** (`config.js`)
   - API keys and endpoints
   - App settings and constants
   - Cache configuration

2. **Storage Layer** (`storage.js`)
   - Local storage management
   - Watchlist persistence
   - User preferences
   - Search history

3. **API Layer** (`api.js`)
   - TMDB API integration
   - Response caching
   - Error handling
   - Image URL generation

4. **Application Layer** (`app.js`)
   - UI logic and rendering
   - User interactions
   - Navigation management
   - Content display

## 🔧 Configuration

### API Settings

Edit `app/src/main/assets/www/js/config.js`:

```javascript
const CONFIG = {
  TMDB: {
    API_KEY: 'your-tmdb-api-key',
    BASE_URL: 'https://api.themoviedb.org/3'
  },
  STREAMING: {
    BASE_URL: 'https://vidsrc.to/embed'
  }
};
```

### Android Settings

- **Permissions**: `app/src/main/AndroidManifest.xml`
- **Build Config**: `app/build.gradle`
- **Capacitor**: `capacitor.config.json`

## 🚀 Deployment

### GitHub Actions

Automated APK building is configured in `.github/workflows/build-apk.yml`:

- Triggers on push to main branch
- Builds debug APK
- Creates GitHub releases
- Uploads APK artifacts

### Manual Release

```bash
# Build release APK
./gradlew assembleRelease

# Sign APK (if keystore configured)
./gradlew bundleRelease
```

## 🔍 Development

### WebView Debugging

1. Enable USB debugging on Android device
2. Open Chrome and go to `chrome://inspect`
3. Select your device and WebView to debug

### Local Development

The web application can be tested in any modern browser by opening `app/src/main/assets/www/index.html`.

### Adding Features

1. **New API Endpoints**: Add methods to `api.js`
2. **Storage Features**: Extend `storage.js`
3. **UI Components**: Update `app.js` and `styles.css`
4. **Configuration**: Modify `config.js`

## 📱 Features in Detail

### Content Discovery
- Trending movies and TV shows
- Popular content recommendations
- Genre-based browsing
- Detailed content information with cast and crew

### Search Functionality
- Real-time search across all content
- Search history tracking
- Multi-category results (movies, TV shows)
- Search suggestions

### Watchlist Management
- Add/remove content from personal watchlist
- Persistent storage across app sessions
- Quick access from bottom navigation
- Visual indicators for watchlisted content

### Streaming Integration
- SPlayer app integration for video playback
- Automatic streaming URL generation
- Fallback handling for missing player apps
- Support for both movies and TV series

## 🐛 Troubleshooting

### Common Issues

1. **Build Failures**
   - Clean project: `./gradlew clean`
   - Check Java/Android SDK versions
   - Verify internet connection for dependencies

2. **WebView Issues**
   - Check asset file placement
   - Verify Android permissions
   - Enable WebView debugging

3. **API Problems**
   - Verify TMDB API key in config.js
   - Check network connectivity
   - Review API rate limits

### Performance Tips

- App includes built-in API response caching
- Images load lazily for better performance
- Local storage used for offline functionality
- Minimal external dependencies

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes in the `android/` folder
4. Test the APK build
5. Submit a pull request

---

**Note**: This is a completely self-contained Android application. Everything needed to build and run the app is contained within the `android/` folder. No separate server setup or client build process is required.