// ================================================================
// API PROVIDERS CONFIGURATION
// ================================================================
// This is where you configure different movie and TV show providers
// Add your API keys and endpoints here

require('dotenv').config();

const API_PROVIDERS = {
  // Primary movie/TV show database API (TMDB)
  TMDB: {
    baseUrl: 'https://api.themoviedb.org/3',
    apiKey: process.env.TMDB_API_KEY,
    imageBaseUrl: 'https://image.tmdb.org/t/p/w500',
    backdropBaseUrl: 'https://image.tmdb.org/t/p/original'
  },

  // Streaming providers - VidSrc.to for streaming embeds
  STREAMING_PROVIDERS: {
    VIDSRC: {
      name: 'VidSrc',
      baseUrl: process.env.VIDSRC_BASE_URL || 'https://vidsrc.to',
      embedUrl: process.env.VIDSRC_EMBED_URL || 'https://vidsrc.to/embed',
      apiUrl: process.env.VIDSRC_API_URL || 'https://vidsrc.to/vapi',
      enabled: true
    }
  },

  // Download providers - Note: Only enable for content you have rights to distribute
  DOWNLOAD_PROVIDERS: {
    // Add your legitimate download providers here
    // DIRECT_DOWNLOAD: {
    //   name: 'DirectDownload',
    //   baseUrl: 'https://your-legitimate-download-provider.com/api',
    //   apiKey: 'YOUR_API_KEY',
    //   enabled: false,
    //   supportedFormats: ['mp4', 'mkv', 'avi']
    // }
  }
};

// Request headers configuration
const REQUEST_HEADERS = {
  'User-Agent': 'OnStreamClone/1.0',
  'Accept': 'application/json',
  'Content-Type': 'application/json'
};

// Rate limiting configuration
const RATE_LIMITS = {
  requestsPerMinute: 40,
  requestsPerHour: 1000
};

module.exports = {
  API_PROVIDERS,
  REQUEST_HEADERS,
  RATE_LIMITS
};
