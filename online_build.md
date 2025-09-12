# 🌐 Build APK Online (No Android Studio Needed)

## Using GitHub Actions to Build APK

### Step 1: Create GitHub Repository
1. Go to https://github.com and create new repository
2. Upload your entire `mtv-testing` folder to it

### Step 2: Add GitHub Action
Create `.github/workflows/build-apk.yml` in your repo:

```yaml
name: Build APK
on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'
        
    - name: Setup Java
      uses: actions/setup-java@v3
      with:
        distribution: 'temurin'
        java-version: '17'
        
    - name: Install dependencies
      run: |
        npm install
        npm install --prefix client
        
    - name: Build React app
      run: npm run build --prefix client
      
    - name: Sync Capacitor
      run: npx cap sync
      
    - name: Build APK
      run: |
        cd android
        chmod +x gradlew
        ./gradlew assembleDebug
        
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: MTV-APK
        path: android/app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Download APK
1. Go to Actions tab in your GitHub repo
2. Click on latest build
3. Download the APK artifact
4. Transfer to your Android and install!

## Alternative: Use AppGyver/Expo Build Service
1. Sign up for a service like EAS Build
2. Upload your project
3. Build APK online
4. Download and install

Your APK will be ready for Android installation! 📱
