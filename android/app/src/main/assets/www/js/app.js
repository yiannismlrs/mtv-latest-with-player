class MTVApp {
  constructor() {
    this.currentPage = 'home';
    this.watchlist = storageService.getWatchlist();
    this.navigationHistory = ['home'];
    this.currentTabData = {
      movies: { activeTab: 'trending', data: null },
      tv: { activeTab: 'trending', data: null }
    };
    
    this.init();
  }

  init() {
    this.setupEventListeners();
    this.loadHomePage();
  }

  setupEventListeners() {
    // Handle Android back button
    document.addEventListener('backbutton', () => this.handleBackButton(), false);
    
    // Handle browser back button
    window.addEventListener('popstate', () => this.handleBackButton());
  }

  // Navigation History Management
  pushToHistory(page) {
    if (this.navigationHistory[this.navigationHistory.length - 1] !== page) {
      this.navigationHistory.push(page);
    }
    this.currentPage = page;
  }

  handleBackButton() {
    if (this.navigationHistory.length > 1) {
      this.navigationHistory.pop();
      const previousPage = this.navigationHistory[this.navigationHistory.length - 1];
      this.navigateToPage(previousPage, false);
    }
    // If we're at home, let the system handle the back button (exit app)
  }

  navigateToPage(page, addToHistory = true) {
    if (addToHistory) {
      this.pushToHistory(page);
    }
    
    switch(page) {
      case 'home':
        this.loadHomePage();
        this.setActiveNav(0);
        break;
      case 'movies':
        this.showMoviesWithTabs();
        this.setActiveNav(1);
        break;
      case 'tv':
        this.showTVWithTabs();
        this.setActiveNav(2);
        break;
      case 'watchlist':
        this.showWatchlist();
        this.setActiveNav(3);
        break;
      case 'search':
        this.showSearchPage();
        break;
    }
  }

  // Search Page
  showSearchPage() {
    console.log('showSearchPage called');
    console.log('Navigation history before:', this.navigationHistory);
    this.pushToHistory('search');
    console.log('Navigation history after:', this.navigationHistory);
    const mainContent = document.querySelector('.main-content');
    mainContent.innerHTML = `
      <div class="search-page">
        <div class="search-header">
          <button class="back-button" onclick="window.app.goBack(); return false;">
            <span>← Back</span>
          </button>
          <div class="search-input-container">
            <input type="text" class="search-input" placeholder="Search movies, TV shows..." id="searchPageInput" autocomplete="off">
          </div>
        </div>
        
        <div id="searchSuggestions" class="search-suggestions"></div>
        
        <div id="topSearched">
          <div class="content-section">
            <div class="section-header">
              <h2 class="section-title">🔍 Top Searches</h2>
            </div>
            <div id="topSearchedContent" class="content-grid"></div>
          </div>
        </div>
        
        <div id="searchResults" style="display: none;"></div>
      </div>
    `;
    
    this.setupSearchPageListeners();
    this.loadTopSearched();
  }

  setupSearchPageListeners() {
    const searchInput = document.getElementById('searchPageInput');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => this.handleSearchInput(e.target.value));
      searchInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter' && e.target.value.trim()) {
          this.performSearchFromPage(e.target.value.trim());
        }
      });
    }
  }

  handleSearchInput(query) {
    if (query.trim().length > 0) {
      this.showSearchSuggestions(query);
    } else {
      document.getElementById('searchSuggestions').innerHTML = '';
      document.getElementById('topSearched').style.display = 'block';
      document.getElementById('searchResults').style.display = 'none';
    }
  }

  showSearchSuggestions(query) {
    const suggestions = [
      `${query} movie`, 
      `${query} tv show`, 
      `${query} series`, 
      `${query} film`
    ];
    
    const suggestionsHTML = suggestions.map(suggestion => 
      `<div class="suggestion-item" onclick="app.performSearchFromPage('${suggestion}')">${suggestion}</div>`
    ).join('');
    
    document.getElementById('searchSuggestions').innerHTML = suggestionsHTML;
  }

  async loadTopSearched() {
    try {
      const [topRatedMovies, topRatedTV, trendingMovies, trendingTV] = await Promise.all([
        apiService.getTopRatedMovies(),
        apiService.getTopRatedTV(),
        apiService.getTrendingMovies('week'),
        apiService.getTrendingTV('week')
      ]);
      
      this.renderTopSearched(
        topRatedMovies?.results || [], 
        topRatedTV?.results || [],
        trendingMovies?.results || [],
        trendingTV?.results || []
      );
    } catch (error) {
      console.error('Failed to load top searched content:', error);
    }
  }

  renderTopSearched(topRatedMovies, topRatedTV, trendingMovies, trendingTV) {
    // Mix different types of popular content to create diverse "Top Searches"
    const mixedContent = [
      ...topRatedMovies.slice(0, 3).map(item => ({...item, type: 'movie'})),
      ...topRatedTV.slice(0, 2).map(item => ({...item, type: 'tv'})),
      ...trendingMovies.slice(0, 2).map(item => ({...item, type: 'movie'})),
      ...trendingTV.slice(0, 3).map(item => ({...item, type: 'tv'}))
    ];
    
    // Shuffle the array to make it more interesting
    for (let i = mixedContent.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [mixedContent[i], mixedContent[j]] = [mixedContent[j], mixedContent[i]];
    }
    
    document.getElementById('topSearchedContent').innerHTML = 
      mixedContent.slice(0, 10).map(item => this.renderContentCard(item, item.type)).join('');
  }

  async performSearchFromPage(query) {
    if (!query.trim()) return;
    
    console.log('Performing search for:', query);
    
    document.getElementById('topSearched').style.display = 'none';
    document.getElementById('searchSuggestions').innerHTML = '';
    document.getElementById('searchResults').style.display = 'block';
    
    this.showLoadingInSearchResults();
    
    try {
      const [movieResults, tvResults] = await Promise.all([
        apiService.searchMovies(query),
        apiService.searchTV(query)
      ]);
      
      console.log('Movie search results:', movieResults);
      console.log('TV search results:', tvResults);
      
      this.renderSearchPageResults(query, {
        movies: movieResults?.results || [],
        tv: tvResults?.results || []
      });
    } catch (error) {
      console.error('Search failed:', error);
      this.renderSearchError('Search failed. Please try again.');
    }
  }

  showLoadingInSearchResults() {
    document.getElementById('searchResults').innerHTML = `
      <div class="search-loading">
        <div class="spinner"></div>
        <p style="color: var(--text-secondary); text-align: center; margin-top: 1rem;">Searching...</p>
      </div>
    `;
  }

  renderSearchError(message) {
    document.getElementById('searchResults').innerHTML = `
      <div style="text-align: center; padding: 2rem;">
        <p style="color: var(--text-secondary); margin-bottom: 1rem;">${message}</p>
        <button onclick="app.handleBackButton()" style="background: var(--primary-orange); color: white; border: none; padding: 0.75rem 1.5rem; border-radius: 8px; cursor: pointer;">Go Back</button>
      </div>
    `;
  }

  renderSearchPageResults(query, results) {
    console.log('Rendering search results:', results);
    
    const allResults = [...(results.movies || []), ...(results.tv || [])];
    
    console.log('Total results:', allResults.length);
    
    document.getElementById('searchResults').innerHTML = `
      <div class="content-section">
        <div class="section-header">
          <h2 class="section-title">Search Results for "${query}"</h2>
          <span style="color: var(--text-secondary); font-size: 0.9rem;">${allResults.length} results found</span>
        </div>
        ${allResults.length > 0 ? `
          <div class="content-grid">
            ${allResults.map(item => {
              const type = item.title ? 'movie' : 'tv';
              return this.renderContentCard(item, type);
            }).join('')}
          </div>
        ` : `
          <div style="text-align: center; padding: 3rem;">
            <p style="color: var(--text-secondary); font-size: 1.1rem; margin-bottom: 1rem;">No results found for "${query}"</p>
            <p style="color: var(--text-muted); margin-bottom: 2rem;">Try a different search term or browse our top searches above.</p>
            <button onclick="document.getElementById('searchPageInput').value = ''; app.handleSearchInput('')" style="background: var(--primary-orange); color: white; border: none; padding: 0.75rem 1.5rem; border-radius: 8px; cursor: pointer;">Clear Search</button>
          </div>
        `}
      </div>
    `;
  }

  async loadHomePage() {
    this.showLoading(true);
    
    try {
      const [trendingMovies, trendingTV, popularMovies, popularTV] = await Promise.all([
        apiService.getTrendingMovies(),
        apiService.getTrendingTV(),
        apiService.getPopularMovies(),
        apiService.getPopularTV()
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
      <div class="home-hero">
        <div class="hero-card">
          <h1>MTV</h1>
          <p>Stream and discover your favorite movies and TV shows with the best viewing experience.</p>
        </div>
      </div>
      
      ${this.renderSection('🔥 Trending Movies', data.trendingMovies.slice(0, 6), 'movie')}
      ${this.renderSection('📺 Popular TV Shows', data.popularTV.slice(0, 6), 'tv')}
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
    const posterUrl = item.poster_path ? apiService.getImageUrl(item.poster_path) : '';

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


  async openContent(id, type) {
    try {
      const details = type === 'movie' ? 
        await apiService.getMovieDetails(id) : 
        await apiService.getTVDetails(id);
        
      if (details) {
        // Add to recently viewed
        storageService.addToRecentlyViewed({
          id,
          type,
          title: details.title || details.name,
          poster_path: details.poster_path
        });
        
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
    const posterUrl = content.poster_path ? apiService.getImageUrl(content.poster_path) : '';
    const backdropUrl = content.backdrop_path ? apiService.getBackdropUrl(content.backdrop_path) : '';
    
    const isInWatchlist = storageService.isInWatchlist(content.id, type);
    
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
    console.log(`Attempting to play: ${title} (${type} - ${id})`);
    
    // Get additional metadata for better provider resolution
    const contentDetails = this.getCurrentContentDetails();
    const year = contentDetails?.year || '2023'; // Fallback year
    
    // Use provider system instead of direct URL construction
    this.launchSPlayer(id, type, title, year);
  }
  
  getCurrentContentDetails() {
    // Try to extract year and other details from current content detail view
    try {
      const detailElement = document.querySelector('.content-detail');
      if (detailElement) {
        // Look for year in the detail view
        const yearElements = detailElement.querySelectorAll('[data-year], .year, .release-year');
        for (let elem of yearElements) {
          const year = elem.textContent || elem.getAttribute('data-year');
          if (year && year.match(/\d{4}/)) {
            return { year: year.match(/\d{4}/)[0] };
          }
        }
        
        // Fallback: look for year in any text content
        const text = detailElement.textContent;
        const yearMatch = text.match(/\b(19|20)\d{2}\b/);
        if (yearMatch) {
          return { year: yearMatch[0] };
        }
      }
    } catch (error) {
      console.log('Could not extract content details:', error);
    }
    
    return null;
  }
  
  launchSPlayer(mediaId, mediaType, title, year) {
    console.log(`Launching SPlayer for: ${title} (${mediaType} - ${mediaId})`);
    console.log(`Year: ${year}`);
    
    // Check if Android interface is available (running in WebView)
    if (typeof Android !== 'undefined' && typeof Android.openInSPlayer === 'function') {
      console.log('Using provider system through Android interface');
      
      try {
        // Use the new provider-based interface
        Android.openInSPlayer(mediaId, mediaType, title, year);
        console.log('Called provider system successfully');
        return;
      } catch (error) {
        console.error('Error using provider system:', error);
      }
    } else {
      console.log('Android interface not available, using fallback');
    }
    
    // Fallback method for non-Android environments
    console.log('Using fallback method - opening SPlayer website');
    
    try {
      if (typeof Android !== 'undefined' && typeof Android.openExternalUrl === 'function') {
        Android.openExternalUrl(CONFIG.STREAMING.SPLAYER_WEBSITE);
      } else {
        window.open(CONFIG.STREAMING.SPLAYER_WEBSITE, '_blank');
      }
    } catch (error) {
      console.error('Fallback method failed:', error);
      // Last resort - try to open in same window (should be handled by WebView)
      window.location.href = CONFIG.STREAMING.SPLAYER_WEBSITE;
    }
  }

  toggleWatchlist(id, type, title, posterUrl) {
    const isInWatchlist = storageService.isInWatchlist(id, type);
    
    if (isInWatchlist) {
      storageService.removeFromWatchlist(id, type);
    } else {
      storageService.addToWatchlist({
        id,
        type,
        title,
        poster_path: posterUrl.replace(apiService.imageBaseUrl, '')
      });
    }
    
    // Update local reference
    this.watchlist = storageService.getWatchlist();
    
    // Refresh the current view if it's content details
    if (document.querySelector('.content-detail')) {
      this.openContent(id, type);
    }
  }

  async showMovies() {
    this.navigateToPage('movies');
  }

  async showMoviesWithTabs() {
    this.showLoading(true);
    
    try {
      const [latest, trending, popular] = await Promise.all([
        apiService.getNowPlayingMovies(),
        apiService.getTrendingMovies(),
        apiService.getPopularMovies()
      ]);
      
      this.currentTabData.movies = {
        latest: latest?.results || [],
        trending: trending?.results || [],
        popular: popular?.results || [],
        activeTab: this.currentTabData.movies.activeTab || 'trending'
      };
      
      this.renderMoviesWithTabs();
    } catch (error) {
      this.showError('Failed to load movies.');
    } finally {
      this.showLoading(false);
    }
  }

  async showTVShows() {
    this.navigateToPage('tv');
  }

  async showTVWithTabs() {
    this.showLoading(true);
    
    try {
      const [latest, trending, popular] = await Promise.all([
        apiService.getTopRatedTV(), // Using top rated as latest
        apiService.getTrendingTV(),
        apiService.getPopularTV()
      ]);
      
      this.currentTabData.tv = {
        latest: latest?.results || [],
        trending: trending?.results || [],
        popular: popular?.results || [],
        activeTab: this.currentTabData.tv.activeTab || 'trending'
      };
      
      this.renderTVWithTabs();
    } catch (error) {
      this.showError('Failed to load TV shows.');
    } finally {
      this.showLoading(false);
    }
  }

  showWatchlist() {
    this.navigateToPage('watchlist');
    this.watchlist = storageService.getWatchlist();
    this.renderCategoryPage('📋 My Watchlist', this.watchlist, null);
  }

  renderMoviesWithTabs() {
    const contentSections = document.getElementById('contentSections');
    const activeData = this.currentTabData.movies[this.currentTabData.movies.activeTab] || [];
    
    contentSections.innerHTML = `
      <div class="content-section">
        <div class="section-header">
          <h2 class="section-title">🎬 Movies</h2>
        </div>
        
        <div class="tab-container">
          <button class="tab-button ${this.currentTabData.movies.activeTab === 'latest' ? 'active' : ''}" onclick="app.switchMovieTab('latest')">
            Latest
          </button>
          <button class="tab-button ${this.currentTabData.movies.activeTab === 'trending' ? 'active' : ''}" onclick="app.switchMovieTab('trending')">
            Trending
          </button>
          <button class="tab-button ${this.currentTabData.movies.activeTab === 'popular' ? 'active' : ''}" onclick="app.switchMovieTab('popular')">
            Popular
          </button>
        </div>
        
        <div class="content-grid">
          ${activeData.map(item => this.renderContentCard(item, 'movie')).join('')}
        </div>
      </div>
    `;
  }

  renderTVWithTabs() {
    const contentSections = document.getElementById('contentSections');
    const activeData = this.currentTabData.tv[this.currentTabData.tv.activeTab] || [];
    
    contentSections.innerHTML = `
      <div class="content-section">
        <div class="section-header">
          <h2 class="section-title">📺 TV Series</h2>
        </div>
        
        <div class="tab-container">
          <button class="tab-button ${this.currentTabData.tv.activeTab === 'latest' ? 'active' : ''}" onclick="app.switchTVTab('latest')">
            Latest
          </button>
          <button class="tab-button ${this.currentTabData.tv.activeTab === 'trending' ? 'active' : ''}" onclick="app.switchTVTab('trending')">
            Trending
          </button>
          <button class="tab-button ${this.currentTabData.tv.activeTab === 'popular' ? 'active' : ''}" onclick="app.switchTVTab('popular')">
            Popular
          </button>
        </div>
        
        <div class="content-grid">
          ${activeData.map(item => this.renderContentCard(item, 'tv')).join('')}
        </div>
      </div>
    `;
  }

  switchMovieTab(tab) {
    this.currentTabData.movies.activeTab = tab;
    this.renderMoviesWithTabs();
  }

  switchTVTab(tab) {
    this.currentTabData.tv.activeTab = tab;
    this.renderTVWithTabs();
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
    this.navigateToPage('home');
  }

  setActiveNav(index) {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach((item, i) => {
      item.classList.toggle('active', i === index);
    });
  }

  goBack() {
    console.log('goBack called');
    console.log('Current navigation history:', this.navigationHistory);
    console.log('Current page:', this.currentPage);
    
    if (this.navigationHistory.length > 1) {
      this.navigationHistory.pop(); // Remove current page
      const previousPage = this.navigationHistory[this.navigationHistory.length - 1];
      console.log('Navigating back to:', previousPage);
      this.navigateToPage(previousPage, false);
    } else {
      console.log('No previous page, going to home');
      this.navigateToPage('home', false);
    }
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
  window.app = app; // Make app available globally
  console.log('App initialized and available globally');
});
