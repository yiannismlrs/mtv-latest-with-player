# MTV Streaming Platform - Setup & Provider Integration Guide

## 🚀 Quick Start

### Prerequisites
- Node.js (v14 or higher)
- npm or yarn package manager

### Installation & Setup

1. **Install dependencies:**
   ```bash
   npm install
   npm install --prefix client
   ```

2. **Start the application:**
   ```bash
   npm run dev
   ```
   This starts both backend (port 5000) and frontend (port 3000) simultaneously.

3. **Access your app:**
   - Web browser: http://localhost:3000
   - Backend API: http://localhost:5000/api

## 📱 Android Installation (PWA)

Your MTV app is ready for Android installation as a Progressive Web App (PWA):

1. **Open in Chrome on Android:**
   - Navigate to your deployed app URL
   - Chrome will show "Add to Home Screen" prompt

2. **Manual Installation:**
   - Tap Chrome menu (⋮)
   - Select "Add to Home Screen"
   - App will install like a native Android app

3. **Features when installed:**
   - App icon on home screen
   - Splash screen with MTV logo
   - Fullscreen experience (no browser bars)
   - Offline caching capability

## 🔧 Adding Your Streaming Providers

### Step 1: Configure Provider Details

Edit `server/config/apiProviders.js`:

```javascript
STREAMING_PROVIDERS: {
  VIDSRC: {
    name: 'VidSrc',
    baseUrl: process.env.VIDSRC_BASE_URL || 'https://vidsrc.to',
    embedUrl: process.env.VIDSRC_EMBED_URL || 'https://vidsrc.to/embed',
    apiUrl: process.env.VIDSRC_API_URL || 'https://vidsrc.to/vapi',
    enabled: true
  },
  
  // Add your own providers here:
  MY_PROVIDER: {
    name: 'MyStreamingProvider',
    baseUrl: 'https://my-provider.com',
    embedUrl: 'https://my-provider.com/embed',
    apiKey: process.env.MY_PROVIDER_API_KEY,
    enabled: true
  }
}
```

### Step 2: Implement Provider Logic

Edit `server/utils/apiIntegration.js` and add your provider methods:

#### For Movies:
```javascript
async getMyProviderLinks(contentType, contentId, title, year) {
  try {
    if (contentType === 'movie') {
      const embedUrl = `${API_PROVIDERS.STREAMING_PROVIDERS.MY_PROVIDER.embedUrl}/movie/${contentId}`;
      return [{
        provider: 'MyProvider',
        quality: 'HD',
        url: embedUrl,
        type: 'embed'
      }];
    }
    return [];
  } catch (error) {
    console.error('My Provider Error:', error.message);
    return [];
  }
}
```

#### For TV Shows:
```javascript
// Add to getMyProviderLinks method:
if (contentType === 'tv') {
  const embedUrl = `${API_PROVIDERS.STREAMING_PROVIDERS.MY_PROVIDER.embedUrl}/tv/${contentId}`;
  return [{
    provider: 'MyProvider',
    quality: 'HD',
    url: embedUrl,
    type: 'embed'
  }];
}
```

#### For TV Show Seasons:
```javascript
// Season-specific embed:
const seasonEmbedUrl = `${API_PROVIDERS.STREAMING_PROVIDERS.MY_PROVIDER.embedUrl}/tv/${contentId}/season/${seasonNumber}`;
```

#### For TV Show Episodes:
```javascript
// Episode-specific embed:
const episodeEmbedUrl = `${API_PROVIDERS.STREAMING_PROVIDERS.MY_PROVIDER.embedUrl}/tv/${contentId}/${seasonNumber}/${episodeNumber}`;
```

### Step 3: Enable Your Provider

In `server/utils/apiIntegration.js`, add your provider to the main aggregator:

```javascript
async getStreamingLinks(contentType, contentId, title, year) {
  const streamingLinks = [];

  // VidSrc Integration (existing)
  if (API_PROVIDERS.STREAMING_PROVIDERS.VIDSRC.enabled) {
    try {
      const vidsrcLinks = await this.getVidSrcLinks(contentType, contentId, title, year);
      if (vidsrcLinks) {
        streamingLinks.push(...vidsrcLinks);
      }
    } catch (error) {
      console.error('VidSrc Error:', error.message);
    }
  }

  // Your Provider Integration (ADD THIS)
  if (API_PROVIDERS.STREAMING_PROVIDERS.MY_PROVIDER.enabled) {
    try {
      const myProviderLinks = await this.getMyProviderLinks(contentType, contentId, title, year);
      if (myProviderLinks) {
        streamingLinks.push(...myProviderLinks);
      }
    } catch (error) {
      console.error('My Provider Error:', error.message);
    }
  }

  return streamingLinks;
}
```

## 📥 Adding Download Providers

### Step 1: Configure Download Provider

Edit `server/config/apiProviders.js`:

```javascript
DOWNLOAD_PROVIDERS: {
  MY_DOWNLOAD_PROVIDER: {
    name: 'MyDownloadProvider',
    baseUrl: 'https://download-provider.com/api',
    apiKey: process.env.DOWNLOAD_PROVIDER_API_KEY,
    enabled: true,
    supportedFormats: ['mp4', 'mkv', 'avi']
  }
}
```

### Step 2: Implement Download Logic

Edit `server/utils/apiIntegration.js`:

```javascript
async getMyDownloadLinks(contentType, contentId, title, year) {
  try {
    // Make API call to your download provider
    const response = await this.axios.get(
      `${API_PROVIDERS.DOWNLOAD_PROVIDERS.MY_DOWNLOAD_PROVIDER.baseUrl}/search`,
      {
        params: {
          api_key: API_PROVIDERS.DOWNLOAD_PROVIDERS.MY_DOWNLOAD_PROVIDER.apiKey,
          type: contentType,
          query: `${title} ${year}`
        }
      }
    );

    return response.data.results?.map(result => ({
      provider: 'MyDownloadProvider',
      quality: result.quality || 'HD',
      format: result.format || 'mp4',
      size: result.file_size || 'Unknown',
      url: result.download_url,
      type: 'direct'
    }));
  } catch (error) {
    console.error('Download Provider Error:', error.message);
    return [];
  }
}

// Add to getDownloadLinks method:
async getDownloadLinks(contentType, contentId, title, year) {
  const downloadLinks = [];

  if (API_PROVIDERS.DOWNLOAD_PROVIDERS.MY_DOWNLOAD_PROVIDER.enabled) {
    try {
      const directLinks = await this.getMyDownloadLinks(contentType, contentId, title, year);
      if (directLinks) {
        downloadLinks.push(...directLinks);
      }
    } catch (error) {
      console.error('Download Provider Error:', error.message);
    }
  }

  return downloadLinks;
}
```

## 🔑 Environment Variables

Create `server/.env` file:

```bash
# TMDB API (already configured)
TMDB_API_KEY=07e7ef828a6dd88f66ae849920ff5fae

# Your Streaming Providers
MY_PROVIDER_API_KEY=your_streaming_api_key_here
ANOTHER_PROVIDER_API_KEY=your_other_api_key_here

# Download Providers (only for content you have rights to)
DOWNLOAD_PROVIDER_API_KEY=your_download_api_key_here
```

## 🎯 Testing Your Integration

1. **Start the development server:**
   ```bash
   npm run dev
   ```

2. **Test streaming links:**
   - Navigate to any movie/TV show detail page
   - Check if "Watch Now" button appears
   - Click to test if embed loads correctly

3. **Test download links:**
   - Check if "Download" button appears on content with download links
   - Verify download options are displayed

4. **API Testing:**
   ```bash
   # Test movie details with links
   curl "http://localhost:5000/api/movies/550"
   
   # Test TV show details with links  
   curl "http://localhost:5000/api/tvshows/1399"
   ```

## 📱 Building for Production

### For Web Deployment:
```bash
npm run build --prefix client
```

### For Android APK (using Capacitor):
```bash
# Install Capacitor
npm install @capacitor/core @capacitor/cli @capacitor/android

# Initialize Capacitor
npx cap init mtv com.yourcompany.mtv

# Add Android platform
npx cap add android

# Build and sync
npm run build --prefix client
npx cap sync

# Open in Android Studio
npx cap open android
```

## 🎨 Customization

### Logo Replacement:
- Replace the MTV logo in `client/src/components/MTVLogo.js`
- Update app icons in `client/public/` (favicon.ico, etc.)

### Color Scheme:
- Modify CSS variables in `client/src/App.css`:
  ```css
  :root {
    --primary-orange: #FF6B35;    /* Your primary color */
    --secondary-orange: #FF8C42;  /* Your secondary color */
    --dark-bg: #0D1117;           /* Background color */
  }
  ```

## 🔒 Security Notes

⚠️ **Important:** Only integrate providers you have legal rights to use. Ensure all streaming and download sources comply with copyright laws in your jurisdiction.

## 🐛 Troubleshooting

### Common Issues:

1. **CORS Errors:** Update provider URLs or configure CORS headers
2. **API Keys:** Verify all environment variables are set correctly  
3. **Embed Not Loading:** Check if provider allows iframe embedding
4. **PWA Not Installing:** Ensure manifest.json is valid and served over HTTPS in production

### Debug Mode:
```bash
# Enable debug logging
DEBUG=* npm run server
```

## 📞 Support

Your MTV streaming platform is now ready! The app includes:
- ✅ TMDB integration for movie/TV metadata
- ✅ Provider configuration system
- ✅ Video streaming with embed support  
- ✅ Download functionality framework
- ✅ Search and browsing
- ✅ Android PWA installation
- ✅ MTV branding and OnStream-style UI

Add your licensed streaming and download providers using the guide above, and your platform will be fully functional!
