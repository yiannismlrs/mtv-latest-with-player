// MTV Streaming App - API Module
class APIService {
  constructor() {
    this.apiKey = CONFIG.TMDB.API_KEY;
    this.baseUrl = CONFIG.TMDB.BASE_URL;
    this.imageBaseUrl = CONFIG.TMDB.IMAGE_BASE_URL;
    this.backdropBaseUrl = CONFIG.TMDB.BACKDROP_BASE_URL;
    this.cache = new Map();
  }

  async request(endpoint, params = {}) {
    try {
      const cacheKey = `${endpoint}_${JSON.stringify(params)}`;
      
      // Check cache first
      if (this.cache.has(cacheKey)) {
        const cached = this.cache.get(cacheKey);
        if (Date.now() - cached.timestamp < CONFIG.CACHE.DURATION) {
          return cached.data;
        }
      }

      const url = new URL(`${this.baseUrl}${endpoint}`);
      url.searchParams.append('api_key', this.apiKey);
      
      Object.keys(params).forEach(key => {
        url.searchParams.append(key, params[key]);
      });

      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data = await response.json();
      
      // Cache the result
      this.cache.set(cacheKey, {
        data,
        timestamp: Date.now()
      });
      
      // Clean cache if it gets too large
      if (this.cache.size > CONFIG.CACHE.MAX_ITEMS) {
        const firstKey = this.cache.keys().next().value;
        this.cache.delete(firstKey);
      }
      
      return data;
    } catch (error) {
      console.error('API request failed:', error);
      throw error;
    }
  }

  // Content discovery methods
  async getTrendingMovies(timeWindow = 'day') {
    return this.request(`/trending/movie/${timeWindow}`);
  }

  async getTrendingTV(timeWindow = 'day') {
    return this.request(`/trending/tv/${timeWindow}`);
  }

  async getPopularMovies(page = 1) {
    return this.request('/movie/popular', { page });
  }

  async getPopularTV(page = 1) {
    return this.request('/tv/popular', { page });
  }

  async getTopRatedMovies(page = 1) {
    return this.request('/movie/top_rated', { page });
  }

  async getTopRatedTV(page = 1) {
    return this.request('/tv/top_rated', { page });
  }

  async getUpcomingMovies(page = 1) {
    return this.request('/movie/upcoming', { page });
  }

  async getNowPlayingMovies(page = 1) {
    return this.request('/movie/now_playing', { page });
  }

  // Search methods
  async searchMovies(query, page = 1) {
    return this.request('/search/movie', { query, page });
  }

  async searchTV(query, page = 1) {
    return this.request('/search/tv', { query, page });
  }

  async searchMulti(query, page = 1) {
    return this.request('/search/multi', { query, page });
  }

  // Details methods
  async getMovieDetails(id) {
    return this.request(`/movie/${id}`, { append_to_response: 'credits,videos,similar' });
  }

  async getTVDetails(id) {
    return this.request(`/tv/${id}`, { append_to_response: 'credits,videos,similar' });
  }

  // Genre methods
  async getMovieGenres() {
    return this.request('/genre/movie/list');
  }

  async getTVGenres() {
    return this.request('/genre/tv/list');
  }

  // Discover methods
  async discoverMovies(params = {}) {
    return this.request('/discover/movie', params);
  }

  async discoverTV(params = {}) {
    return this.request('/discover/tv', params);
  }

  // Utility methods
  getImageUrl(path, size = 'w500') {
    if (!path) return '';
    return `https://image.tmdb.org/t/p/${size}${path}`;
  }

  getBackdropUrl(path, size = 'w1280') {
    if (!path) return '';
    return `https://image.tmdb.org/t/p/${size}${path}`;
  }

  clearCache() {
    this.cache.clear();
  }
}

// Create global API instance
const apiService = new APIService();