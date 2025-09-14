// MTV Streaming App - Configuration
const CONFIG = {
  // TMDB API Configuration
  TMDB: {
    API_KEY: '07e7ef828a6dd88f66ae849920ff5fae',
    BASE_URL: 'https://api.themoviedb.org/3',
    IMAGE_BASE_URL: 'https://image.tmdb.org/t/p/w500',
    BACKDROP_BASE_URL: 'https://image.tmdb.org/t/p/w1280'
  },
  
  // Streaming Configuration
  STREAMING: {
    BASE_URL: 'https://vidsrc.to/embed',
    SPLAYER_SCHEME: 'intent://play',
    SPLAYER_PACKAGE: 'com.ttee.leeplayer',
    SPLAYER_WEBSITE: 'https://splayer.dev/',
    SPLAYER_PLAYSTORE: 'https://play.google.com/store/apps/details?id=com.ttee.leeplayer',
    FALLBACK_URL: 'https://splayer.dev'
  },
  
  // App Configuration
  APP: {
    NAME: 'MTV Streaming',
    VERSION: '1.0.0',
    STORAGE_PREFIX: 'mtv_',
    DEFAULT_LANGUAGE: 'en-US'
  },
  
  // Cache Configuration
  CACHE: {
    DURATION: 24 * 60 * 60 * 1000, // 24 hours in milliseconds
    MAX_ITEMS: 1000
  }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
  module.exports = CONFIG;
}