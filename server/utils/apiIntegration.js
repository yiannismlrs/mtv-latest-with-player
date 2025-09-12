const axios = require('axios');
const { API_PROVIDERS, REQUEST_HEADERS } = require('../config/apiProviders');

class ApiIntegration {
  constructor() {
    this.axios = axios.create({
      headers: REQUEST_HEADERS,
      timeout: 10000
    });
  }

  // ================================================================
  // TMDB API INTEGRATION
  // ================================================================
  async searchMoviesTMDB(query, page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.TMDB.baseUrl}/search/movie`,
        {
          params: {
            api_key: API_PROVIDERS.TMDB.apiKey,
            query: query,
            page: page
          }
        }
      );
      return response.data;
    } catch (error) {
      console.error('TMDB Movie Search Error:', error.message);
      return null;
    }
  }

  async searchTVShowsTMDB(query, page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.TMDB.baseUrl}/search/tv`,
        {
          params: {
            api_key: API_PROVIDERS.TMDB.apiKey,
            query: query,
            page: page
          }
        }
      );
      return response.data;
    } catch (error) {
      console.error('TMDB TV Search Error:', error.message);
      return null;
    }
  }

  async getMovieDetailsTMDB(movieId) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.TMDB.baseUrl}/movie/${movieId}`,
        {
          params: {
            api_key: API_PROVIDERS.TMDB.apiKey
          }
        }
      );
      return response.data;
    } catch (error) {
      console.error('TMDB Movie Details Error:', error.message);
      return null;
    }
  }

  async getTVShowDetailsTMDB(tvId) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.TMDB.baseUrl}/tv/${tvId}`,
        {
          params: {
            api_key: API_PROVIDERS.TMDB.apiKey
          }
        }
      );
      return response.data;
    } catch (error) {
      console.error('TMDB TV Details Error:', error.message);
      return null;
    }
  }

  async getPopularMoviesTMDB(page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.TMDB.baseUrl}/movie/popular`,
        {
          params: {
            api_key: API_PROVIDERS.TMDB.apiKey,
            page: page
          }
        }
      );
      return response.data;
    } catch (error) {
      console.error('TMDB Popular Movies Error:', error.message);
      return null;
    }
  }

  async getTrendingMoviesTMDB(page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.TMDB.baseUrl}/trending/movie/day`,
        {
          params: {
            api_key: API_PROVIDERS.TMDB.apiKey,
            page: page
          }
        }
      );
      return response.data;
    } catch (error) {
      console.error('TMDB Trending Movies Error:', error.message);
      return null;
    }
  }

  async getPopularTVShowsTMDB(page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.TMDB.baseUrl}/tv/popular`,
        {
          params: {
            api_key: API_PROVIDERS.TMDB.apiKey,
            page: page
          }
        }
      );
      return response.data;
    } catch (error) {
      console.error('TMDB Popular TV Shows Error:', error.message);
      return null;
    }
  }

  async getTrendingTVShowsTMDB(page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.TMDB.baseUrl}/trending/tv/day`,
        {
          params: {
            api_key: API_PROVIDERS.TMDB.apiKey,
            page: page
          }
        }
      );
      return response.data;
    } catch (error) {
      console.error('TMDB Trending TV Shows Error:', error.message);
      return null;
    }
  }

  // ================================================================
  // STREAMING PROVIDERS INTEGRATION
  // ================================================================
  // VidSrc.to integration for streaming
  async getStreamingLinks(contentType, contentId, title, year) {
    const streamingLinks = [];

    // VidSrc Integration
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

    return streamingLinks;
  }

  // ================================================================
  // VIDSRC.TO IMPLEMENTATION
  // ================================================================
  async getVidSrcLinks(contentType, contentId, title, year) {
    try {
      const streamingLinks = [];
      
      // Generate VidSrc embed URLs based on content type
      if (contentType === 'movie') {
        // Movie embed URL: https://vidsrc.to/embed/movie/{id}
        const embedUrl = `${API_PROVIDERS.STREAMING_PROVIDERS.VIDSRC.embedUrl}/movie/${contentId}`;
        streamingLinks.push({
          provider: 'VidSrc',
          quality: 'HD',
          url: embedUrl,
          type: 'embed'
        });
      } else if (contentType === 'tv') {
        // TV show embed URL: https://vidsrc.to/embed/tv/{id}
        const embedUrl = `${API_PROVIDERS.STREAMING_PROVIDERS.VIDSRC.embedUrl}/tv/${contentId}`;
        streamingLinks.push({
          provider: 'VidSrc',
          quality: 'HD',
          url: embedUrl,
          type: 'embed'
        });
      }
      
      return streamingLinks;
    } catch (error) {
      console.error('VidSrc API Error:', error.message);
      return [];
    }
  }

  // ================================================================
  // VIDSRC API METHODS
  // ================================================================
  async getVidSrcLatestMovies(type = 'new', page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.STREAMING_PROVIDERS.VIDSRC.apiUrl}/movie/${type}${page > 1 ? `/${page}` : ''}`
      );
      return response.data;
    } catch (error) {
      console.error('VidSrc Latest Movies Error:', error.message);
      return null;
    }
  }
  
  async getVidSrcLatestTVShows(type = 'new', page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.STREAMING_PROVIDERS.VIDSRC.apiUrl}/tv/${type}${page > 1 ? `/${page}` : ''}`
      );
      return response.data;
    } catch (error) {
      console.error('VidSrc Latest TV Shows Error:', error.message);
      return null;
    }
  }
  
  async getVidSrcLatestEpisodes(page = 1) {
    try {
      const response = await this.axios.get(
        `${API_PROVIDERS.STREAMING_PROVIDERS.VIDSRC.apiUrl}/episode/latest${page > 1 ? `/${page}` : ''}`
      );
      return response.data;
    } catch (error) {
      console.error('VidSrc Latest Episodes Error:', error.message);
      return null;
    }
  }

  // ================================================================
  // DOWNLOAD LINKS INTEGRATION
  // ================================================================
  async getDownloadLinks(contentType, contentId, title, year) {
    // No download providers configured currently
    // Add legitimate download providers in the configuration file
    return [];
  }

}

module.exports = new ApiIntegration();
