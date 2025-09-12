# 📱 Build APK for Your MTV Streaming App

## 🚀 Option 1: Using Android Studio (Recommended)

### Step 1: Install Android Studio
1. Download from: https://developer.android.com/studio
2. Install with default settings
3. Let it download Android SDK

### Step 2: Open Project in Android Studio
```bash
npx cap open android
```
This will open your MTV project in Android Studio.

### Step 3: Build APK
1. In Android Studio, click **Build** menu
2. Select **Build Bundle(s) / APK(s)**
3. Click **Build APK(s)**
4. Wait for build to complete
5. Click **locate** to find your APK file

**APK Location:** `android/app/build/outputs/apk/debug/app-debug.apk`

## 🔧 Option 2: Command Line Build (Advanced)

### Prerequisites:
- Java JDK 8 or 11
- Android SDK
- Gradle

### Commands:
```bash
# Navigate to android folder
cd android

# Build debug APK
./gradlew assembleDebug

# Build release APK (needs signing)
./gradlew assembleRelease
```

## 📲 Install APK on Your Android

### Method 1: Direct Transfer
1. Copy `app-debug.apk` to your Android device
2. Open file manager on Android
3. Tap the APK file
4. Allow "Install unknown apps" if prompted
5. Install the MTV app!

### Method 2: ADB Install (if connected via USB)
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

## 🎯 Quick Build Script

Run this to build everything automatically:
```bash
# Build React app
npm run build --prefix client

# Sync with Capacitor
npx cap sync

# Open in Android Studio (to build APK)
npx cap open android
```

## 📋 Your APK Details:
- **App Name:** MTV
- **Package ID:** com.mtv.streaming
- **Features:** 
  - Full streaming interface
  - Search functionality
  - Download options
  - Offline capability
  - MTV branding

## 🔑 Signing for Release (Optional)
For a production APK that can be shared:

1. Generate keystore:
```bash
keytool -genkey -v -keystore my-release-key.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
```

2. Update `android/app/build.gradle` with signing config
3. Build release APK: `./gradlew assembleRelease`

Your MTV streaming app is ready to install on Android! 🎉
