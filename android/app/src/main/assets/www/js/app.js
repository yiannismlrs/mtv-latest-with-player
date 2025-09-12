// MTV Streaming App - Main JavaScript
class MTVApp {
  constructor() {
    this.currentPage = 'home';
    this.apiKey = '07e7ef828a6dd88f66ae849920ff5fae';
    this.tmdbBaseUrl = 'https://api.themoviedb.org/3';
    this.imageBaseUrl = 'https://image.tmdb.org/t/p/w500';
    this.backdropBaseUrl = 'https://image.tmdb.org/t/p/w1280';
    this.watchlist = this.getWatchlist();
    
    this.init();
  }

  init() {
    this.setupEventListeners();
    this.loadHomePage();
  }

  setupEventListeners() {
    const searchBtn = document.getElementById('searchBtn');
    const searchInput = document.getElementById('searchInput');
    
    searchBtn.addEventListener('click', () => this.performSearch());
    searchInput.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') this.performSearch();
    });
  }

  async apiRequest(endpoint, params = {}) {
    try {
      const url = new URL(`${this.tmdbBaseUrl}${endpoint}`);
      url.searchParams.append('api_key', this.apiKey);
      
      Object.keys(params).forEach(key => {
        url.searchParams.append(key, params[key]);
      });

      const response = await fetch(url);
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
      
      return await response.json();
    } catch (error) {
      console.error('API request failed:', error);
      return null;
    }
  }

  async loadHomePage() {
    this.showLoading(true);
    
    try {
      const [trendingMovies, trendingTV, popularMovies, popularTV] = await Promise.all([
        this.apiRequest('/trending/movie/day'),
        this.apiRequest('/trending/tv/day'),
        this.apiRequest('/movie/popular'),
        this.apiRequest('/tv/popular')
      ]);

      this.renderHomePage({
        trendingMovies: trendingMovies?.results || [],
        trendingTV: trendingTV?.results || [],
        popularMovies: popularMovies?.results || [],
        popularTV: popularTV?.results || []
      });
    } catch (error) {
      console.error('Failed to load home page:', error);
      this.showError('Failed to load content. Please try again.');
    } finally {
      this.showLoading(false);
    }
  }

  renderHomePage(data) {
    const contentSections = document.getElementById('contentSections');
    contentSections.innerHTML = `
      ${this.renderSection('🔥 Trending Movies', data.trendingMovies.slice(0, 8), 'movie')}
      ${this.renderSection('📺 Trending TV Shows', data.trendingTV.slice(0, 8), 'tv')}
      ${this.renderSection('🎬 Popular Movies', data.popularMovies.slice(0, 8), 'movie')}
      ${this.renderSection('⭐ Popular TV Shows', data.popularTV.slice(0, 8), 'tv')}
    `;
  }

  renderSection(title, items, type) {
    if (!items.length) return '';
    
    return `
      <div class="content-section">
        <div class="section-header">
          <h2 class="section-title">${title}</h2>
        </div>
        <div class="content-grid">
          ${items.map(item => this.renderContentCard(item, type)).join('')}
        </div>
      </div>
    `;
  }

  renderContentCard(item, type) {
    const title = item.title || item.name;
    const releaseDate = item.release_date || item.first_air_date;
    const year = releaseDate ? new Date(releaseDate).getFullYear() : '';
    const rating = item.vote_average ? item.vote_average.toFixed(1) : 'N/A';
    const posterUrl = item.poster_path ? `${this.imageBaseUrl}${item.poster_path}` : '';

    return `
      <div class="content-card" onclick="app.openContent(${item.id}, '${type}')">
        <div class="card-poster" style="background-image: url('${posterUrl}')"></div>
        <div class="card-content">
          <div class="card-title">${title}</div>
          <div class="card-meta">
            <span class="card-year">${year}</span>
            <div class="rating">
              <span>⭐</span>
              <span>${rating}</span>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  async performSearch() {
    const query = document.getElementById('searchInput').value.trim();
    if (!query) return;

    this.showLoading(true);
    
    try {
      const [movieResults, tvResults] = await Promise.all([
        this.apiRequest('/search/movie', { query }),
        this.apiRequest('/search/tv', { query })
      ]);

      this.renderSearchResults(query, {
        movies: movieResults?.results || [],
        tv: tvResults?.results || []
      });
    } catch (error) {
      console.error('Search failed:', error);
      this.showError('Search failed. Please try again.');
    } finally {
      this.showLoading(false);
    }
  }

  renderSearchResults(query, results) {
    const contentSections = document.getElementById('contentSections');
    const allResults = [...results.movies, ...results.tv];
    
    contentSections.innerHTML = `
      <div class="content-section">
        <div class="section-header">
          <h2 class="section-title">Search Results for "${query}"</h2>
        </div>
        ${allResults.length > 0 ? `
          <div class="content-grid">
            ${allResults.map(item => {
              const type = item.title ? 'movie' : 'tv';
              return this.renderContentCard(item, type);
            }).join('')}
          </div>
        ` : '<p style="color: var(--text-secondary); text-align: center; padding: 2rem;">No results found. Try a different search term.</p>'}
      </div>
    `;
  }

  async openContent(id, type) {
    try {
      const details = await this.apiRequest(`/${type}/${id}`);
      if (details) {
        this.showContentDetails(details, type);
      }
    } catch (error) {
      console.error('Failed to load content details:', error);
      this.showError('Failed to load content details.');
    }
  }

  showContentDetails(content, type) {
    const title = content.title || content.name;
    const releaseDate = content.release_date || content.first_air_date;
    const year = releaseDate ? new Date(releaseDate).getFullYear() : '';
    const rating = content.vote_average ? content.vote_average.toFixed(1) : 'N/A';
    const posterUrl = content.poster_path ? `${this.imageBaseUrl}${content.poster_path}` : '';
    const backdropUrl = content.backdrop_path ? `${this.backdropBaseUrl}${content.backdrop_path}` : '';
    
    const isInWatchlist = this.isInWatchlist(content.id, type);
    
    const contentSections = document.getElementById('contentSections');
    contentSections.innerHTML = `
      <div class="content-detail">
        <button onclick="app.goBack()" style="background: var(--card-bg); border: 1px solid var(--border-color); color: var(--text-primary); padding: 0.5rem 1rem; border-radius: 8px; margin-bottom: 1rem; cursor: pointer;">← Back</button>
        
        <div class="detail-hero" style="position: relative; min-height: 60vh; background: linear-gradient(180deg, transparent 0%, var(--dark-bg) 100%); border-radius: 15px; overflow: hidden; margin-bottom: 2rem;">
          <div style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; opacity: 0.6; background-image: url('${backdropUrl}'); background-size: cover; background-position: center;"></div>
          
          <div style="position: relative; z-index: 2; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; padding: 2rem; text-align: center;">
            <div style="margin-bottom: 1rem;">
              <img src="${posterUrl}" alt="${title}" style="width: 150px; height: auto; border-radius: 12px; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);">
            </div>
            
            <div>
              <h1 style="font-size: 2rem; margin-bottom: 1rem; color: var(--text-primary); font-weight: bold;">${title}</h1>
              <div style="display: flex; align-items: center; justify-content: center; gap: 0.5rem; color: var(--text-secondary); font-size: 0.9rem; margin-bottom: 1rem;">
                <span>📅</span><span>${year}</span>
                <span>•</span><span>⭐</span><span style="color: var(--primary-orange); font-weight: bold;">${rating}</span>
              </div>
            </div>
          </div>
        </div>

        <div style="max-width: 600px; margin: 0 auto; padding: 0 1rem;">
          <div style="display: flex; gap: 1rem; margin-bottom: 2rem;">
            <button onclick="app.watchContent(${content.id}, '${type}', '${title}')" style="flex: 1; background: var(--primary-orange); color: white; border: none; padding: 1rem 2rem; border-radius: 8px; font-weight: bold; font-size: 1rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 0.5rem;">
              ▶️ Watch
            </button>
            <button onclick="app.toggleWatchlist(${content.id}, '${type}', '${title.replace(/'/g, "\\'")}', '${posterUrl}')" style="flex: 1; background: var(--card-bg); color: var(--text-primary); border: 1px solid var(--border-color); padding: 1rem 2rem; border-radius: 8px; font-weight: bold; font-size: 1rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 0.5rem;">
              ${isInWatchlist ? '❌ Remove' : '📋 Add List'}
            </button>
          </div>

          <div style="margin-bottom: 2rem;">
            <h3 style="color: var(--text-primary); font-size: 1.1rem; margin-bottom: 0.5rem; font-weight: bold;">Overview</h3>
            <p style="color: var(--text-secondary); line-height: 1.6; font-size: 0.9rem;">${content.overview || 'No overview available.'}</p>
          </div>

          ${content.genres && content.genres.length > 0 ? `
            <div style="margin-bottom: 1.5rem;">
              <h3 style="color: var(--text-primary); font-size: 1.1rem; margin-bottom: 0.5rem; font-weight: bold;">Genres</h3>
              <p style="color: var(--text-secondary); line-height: 1.5;">${content.genres.map(g => g.name).join(', ')}</p>
            </div>
          ` : ''}
        </div>
      </div>
    `;
  }

  watchContent(id, type, title) {
    // Generate streaming URL for SPlayer
    const baseUrl = 'https://vidsrc.to/embed';
    const path = type === 'movie' ? 'movie' : 'tv';
    const streamUrl = `${baseUrl}/${path}/${id}`;
    
    // Try to open in SPlayer app
    const splayerUrl = `splayer://play?url=${encodeURIComponent(streamUrl)}&title=${encodeURIComponent(title)}`;
    window.location.href = splayerUrl;
    
    // Fallback: show instructions
    setTimeout(() => {
      alert('Opening in SPlayer... If SPlayer is not installed, please download it from https://splayer.org');
    }, 1000);
  }

  // Watchlist management
  getWatchlist() {
    const watchlist = localStorage.getItem('mtv_watchlist');
    return watchlist ? JSON.parse(watchlist) : [];
  }

  saveWatchlist() {
    localStorage.setItem('mtv_watchlist', JSON.stringify(this.watchlist));
  }

  isInWatchlist(id, type) {
    return this.watchlist.some(item => item.id === id && item.type === type);
  }

  toggleWatchlist(id, type, title, posterUrl) {
    const existingIndex = this.watchlist.findIndex(item => item.id === id && item.type === type);
    
    if (existingIndex >= 0) {
      this.watchlist.splice(existingIndex, 1);
    } else {
      this.watchlist.unshift({
        id,
        type,
        title,
        poster_path: posterUrl.replace(this.imageBaseUrl, ''),
        addedAt: Date.now()
      });
    }
    
    this.saveWatchlist();
    
    // Refresh the current view if it's content details
    if (document.querySelector('.content-detail')) {
      this.openContent(id, type);
    }
  }

  async showMovies() {
    this.setActiveNav(1);
    this.showLoading(true);
    
    try {
      const data = await this.apiRequest('/movie/popular');
      this.renderCategoryPage('🎬 Popular Movies', data?.results || [], 'movie');
    } catch (error) {
      this.showError('Failed to load movies.');
    } finally {
      this.showLoading(false);
    }
  }

  async showTVShows() {
    this.setActiveNav(2);
    this.showLoading(true);
    
    try {
      const data = await this.apiRequest('/tv/popular');
      this.renderCategoryPage('📺 Popular TV Shows', data?.results || [], 'tv');
    } catch (error) {
      this.showError('Failed to load TV shows.');
    } finally {
      this.showLoading(false);
    }
  }

  showWatchlist() {
    this.setActiveNav(3);
    this.renderCategoryPage('📋 My Watchlist', this.watchlist, null);
  }

  renderCategoryPage(title, items, type) {
    const contentSections = document.getElementById('contentSections');
    contentSections.innerHTML = `
      <div class="content-section">
        <div class="section-header">
          <h2 class="section-title">${title}</h2>
        </div>
        ${items.length > 0 ? `
          <div class="content-grid">
            ${items.map(item => {
              const itemType = type || item.type || (item.title ? 'movie' : 'tv');
              return this.renderContentCard(item, itemType);
            }).join('')}
          </div>
        ` : `<p style="color: var(--text-secondary); text-align: center; padding: 2rem;">No items found.</p>`}
      </div>
    `;
  }

  showHome() {
    this.setActiveNav(0);
    this.loadHomePage();
  }

  setActiveNav(index) {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach((item, i) => {
      item.classList.toggle('active', i === index);
    });
  }

  goBack() {
    this.loadHomePage();
    this.setActiveNav(0);
  }

  showLoading(show) {
    const loading = document.getElementById('loading');
    loading.style.display = show ? 'flex' : 'none';
  }

  showError(message) {
    const contentSections = document.getElementById('contentSections');
    contentSections.innerHTML = `
      <div style="text-align: center; padding: 4rem 2rem;">
        <h2 style="color: var(--text-primary); margin-bottom: 0.5rem;">Error</h2>
        <p style="color: var(--text-secondary);">${message}</p>
        <button onclick="app.loadHomePage()" style="background: var(--primary-orange); color: white; border: none; padding: 0.75rem 2rem; border-radius: 25px; font-weight: bold; cursor: pointer; margin-top: 1rem;">Try Again</button>
      </div>
    `;
  }
}

// Global functions for onclick handlers
function showHome() { app.showHome(); }
function showMovies() { app.showMovies(); }
function showTVShows() { app.showTVShows(); }
function showWatchlist() { app.showWatchlist(); }
function exploreContent() { app.showMovies(); }

// Initialize app when DOM is loaded
let app;
document.addEventListener('DOMContentLoaded', () => {
  app = new MTVApp();
});