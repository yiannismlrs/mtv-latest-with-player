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
      case 'downloads':
        this.showDownloads();
        this.setActiveNav(4);
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
          <div style="display: flex; gap: 0.75rem; margin-bottom: 2rem; flex-wrap: wrap;">
            ${type === 'tv' ? `
              <button onclick="app.showSeasonEpisodeSelection(${content.id}, '${title}')" style="flex: 1; min-width: 120px; background: var(--primary-orange); color: white; border: none; padding: 0.875rem 1.5rem; border-radius: 8px; font-weight: bold; font-size: 0.95rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 0.5rem;">
                📺 Select Episode
              </button>
            ` : `
              <button onclick="app.watchContent(${content.id}, '${type}', '${title}')" style="flex: 1; min-width: 100px; background: var(--primary-orange); color: white; border: none; padding: 0.875rem 1.5rem; border-radius: 8px; font-weight: bold; font-size: 0.95rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 0.5rem;">
                ▶️ Watch
              </button>
              <button onclick="app.downloadContent(${content.id}, '${type}', '${title}', '${year}')" style="flex: 1; min-width: 100px; background: var(--card-bg); color: var(--text-primary); border: 1px solid var(--primary-orange); padding: 0.875rem 1.5rem; border-radius: 8px; font-weight: bold; font-size: 0.95rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 0.5rem;">
                💾 Download
              </button>
            `}
            <button onclick="app.toggleWatchlist(${content.id}, '${type}', '${title.replace(/'/g, "\\'")}', '${posterUrl}')" style="flex: 1; min-width: 100px; background: var(--card-bg); color: var(--text-primary); border: 1px solid var(--border-color); padding: 0.875rem 1.5rem; border-radius: 8px; font-weight: bold; font-size: 0.95rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 0.5rem;">
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

  async showSeasonEpisodeSelection(tvId, title) {
    console.log(`Showing season/episode selection for: ${title} (${tvId})`);
    
    try {
      // Get TV show details to know available seasons
      const tvDetails = await apiService.getTVDetails(tvId);
      
      if (!tvDetails || !tvDetails.seasons) {
        this.showError('Failed to load season information.');
        return;
      }
      
      // Filter out special seasons (season 0 is usually specials)
      const regularSeasons = tvDetails.seasons.filter(season => season.season_number > 0);
      
      const contentSections = document.getElementById('contentSections');
      contentSections.innerHTML = `
        <div class="season-episode-selection">
          <button onclick="app.goBack()" style="background: var(--card-bg); border: 1px solid var(--border-color); color: var(--text-primary); padding: 0.5rem 1rem; border-radius: 8px; margin-bottom: 1rem; cursor: pointer;">← Back</button>
          
          <div style="text-align: center; margin-bottom: 2rem;">
            <h2 style="color: var(--text-primary); margin-bottom: 0.5rem;">${title}</h2>
            <p style="color: var(--text-secondary);">Select Season & Episode</p>
          </div>
          
          <div class="seasons-container">
            ${regularSeasons.map(season => `
              <div class="season-card" onclick="app.showEpisodesForSeason(${tvId}, ${season.season_number}, '${title}', '${season.name}')">
                <div class="season-poster">
                  <img src="${season.poster_path ? apiService.getImageUrl(season.poster_path, 'w300') : 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzAwIiBoZWlnaHQ9IjQ1MCIgdmlld0JveD0iMCAwIDMwMCA0NTAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjMwMCIgaGVpZ2h0PSI0NTAiIGZpbGw9IiMxODE4MUIiLz48dGV4dCB4PSIxNTAiIHk9IjIzMCIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZmlsbD0iIzUzNTM1NyIgZm9udC1mYW1pbHk9IkFyaWFsIiBmb250LXNpemU9IjE0Ij5ObyBJbWFnZTwvdGV4dD48L3N2Zz4='}" alt="${season.name}" style="width: 100%; border-radius: 8px;">
                </div>
                <div class="season-info">
                  <h3 style="color: var(--text-primary); font-size: 1rem; margin: 0.5rem 0;">${season.name}</h3>
                  <p style="color: var(--text-secondary); font-size: 0.85rem; margin: 0;">${season.episode_count} episodes</p>
                  ${season.air_date ? `<p style="color: var(--text-muted); font-size: 0.8rem; margin: 0.25rem 0 0 0;">${new Date(season.air_date).getFullYear()}</p>` : ''}
                </div>
              </div>
            `).join('')}
          </div>
        </div>
      `;
      
    } catch (error) {
      console.error('Failed to load season information:', error);
      this.showError('Failed to load season information.');
    }
  }
  
  async showEpisodesForSeason(tvId, seasonNumber, showTitle, seasonTitle) {
    console.log(`Loading episodes for: ${showTitle} - ${seasonTitle}`);
    
    this.showLoading(true);
    
    try {
      const seasonDetails = await apiService.getTVSeasonDetails(tvId, seasonNumber);
      
      if (!seasonDetails || !seasonDetails.episodes) {
        this.showError('Failed to load episode information.');
        return;
      }
      
      const contentSections = document.getElementById('contentSections');
      contentSections.innerHTML = `
        <div class="episode-selection">
          <button onclick="app.showSeasonEpisodeSelection(${tvId}, '${showTitle}')" style="background: var(--card-bg); border: 1px solid var(--border-color); color: var(--text-primary); padding: 0.5rem 1rem; border-radius: 8px; margin-bottom: 1rem; cursor: pointer;">← Back to Seasons</button>
          
          <div style="text-align: center; margin-bottom: 2rem;">
            <h2 style="color: var(--text-primary); margin-bottom: 0.5rem;">${showTitle}</h2>
            <h3 style="color: var(--text-secondary); margin: 0;">${seasonTitle}</h3>
          </div>
          
          <div class="episodes-container">
            ${seasonDetails.episodes.map(episode => `
              <div class="episode-card">
                <div class="episode-still" onclick="app.watchTVEpisode(${tvId}, ${seasonNumber}, ${episode.episode_number}, '${showTitle}', '${episode.name}')">
                  <img src="${episode.still_path ? apiService.getImageUrl(episode.still_path, 'w300') : 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzAwIiBoZWlnaHQ9IjE2OSIgdmlld0JveD0iMCAwIDMwMCAxNjkiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjMwMCIgaGVpZ2h0PSIxNjkiIGZpbGw9IiMxODE4MUIiLz48dGV4dCB4PSIxNTAiIHk9Ijg5IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjNTM1MzU3IiBmb250LWZhbWlseT0iQXJpYWwiIGZvbnQtc2l6ZT0iMTQiPk5vIEltYWdlPC90ZXh0Pjwvc3ZnPg=='}" alt="${episode.name}" style="width: 100%; border-radius: 8px;">
                  <div class="play-overlay">
                    <div class="play-button">▶️</div>
                  </div>
                </div>
                <div class="episode-info">
                  <div class="episode-title">
                    <span class="episode-number">E${episode.episode_number}</span>
                    <span class="episode-name">${episode.name}</span>
                  </div>
                  <p class="episode-overview">${episode.overview || 'No description available.'}</p>
                  ${episode.air_date ? `<p class="episode-air-date">Aired: ${new Date(episode.air_date).toLocaleDateString()}</p>` : ''}
                  ${episode.runtime ? `<p class="episode-runtime">${episode.runtime} minutes</p>` : ''}
                  <div class="episode-actions" style="display: flex; gap: 0.5rem; margin-top: 1rem;">
                    <button onclick="app.watchTVEpisode(${tvId}, ${seasonNumber}, ${episode.episode_number}, '${showTitle}', '${episode.name}')" style="flex: 1; background: var(--primary-orange); color: white; border: none; padding: 0.5rem 1rem; border-radius: 6px; font-size: 0.85rem; cursor: pointer;">
                      ▶️ Watch
                    </button>
                    <button onclick="app.downloadTVEpisode(${tvId}, ${seasonNumber}, ${episode.episode_number}, '${showTitle}', '${episode.name}')" style="flex: 1; background: var(--card-bg); color: var(--text-primary); border: 1px solid var(--primary-orange); padding: 0.5rem 1rem; border-radius: 6px; font-size: 0.85rem; cursor: pointer;">
                      💾 Download
                    </button>
                  </div>
                </div>
              </div>
            `).join('')}
          </div>
        </div>
      `;
      
    } catch (error) {
      console.error('Failed to load episodes:', error);
      this.showError('Failed to load episode information.');
    } finally {
      this.showLoading(false);
    }
  }
  
  watchTVEpisode(tvId, season, episode, showTitle, episodeTitle) {
    console.log(`Watching: ${showTitle} S${season}E${episode} - ${episodeTitle}`);
    
    // Get additional metadata for better provider resolution
    const contentDetails = this.getCurrentContentDetails();
    const year = contentDetails?.year || '2023'; // Fallback year
    
    // Use provider system with season and episode info
    this.launchSPlayerWithEpisode(tvId, 'tv', showTitle, year, season, episode);
  }
  
  launchSPlayerWithEpisode(mediaId, mediaType, title, year, season, episode) {
    console.log(`Launching VidLink for: ${title} S${season}E${episode} (${mediaType} - ${mediaId})`);
    console.log(`Year: ${year}`);
    
    // Check if Android interface is available (running in WebView)
    if (typeof Android !== 'undefined' && typeof Android.openInVidLink === 'function') {
      console.log('Using VidLink with season/episode info');
      
      try {
        // Use the VidLink interface with season and episode
        Android.openInVidLink(mediaId, mediaType, title, year, season.toString(), episode.toString());
        console.log('Called VidLink with episode info successfully');
        return;
      } catch (error) {
        console.error('Error using VidLink with episode info:', error);
      }
    } else {
      console.log('Android VidLink interface not available, using fallback');
    }
    
    // Fallback to general TV interface
    this.launchSPlayer(mediaId, mediaType, title, year);
  }

  watchContent(id, type, title) {
    console.log(`Attempting to play: ${title} (${type} - ${id})`);
    
    // Get additional metadata for better provider resolution
    const contentDetails = this.getCurrentContentDetails();
    const year = contentDetails?.year || '2023'; // Fallback year
    
    // Use provider system instead of direct URL construction
    this.launchSPlayer(id, type, title, year);
  }
  
  downloadContent(id, type, title, year) {
    console.log(`Attempting to download: ${title} (${type} - ${id})`);
    
    // Check if Android interface is available (running in WebView)
    if (typeof Android !== 'undefined' && typeof Android.downloadContent === 'function') {
      console.log('Using Android download interface');
      
      try {
        // Use the Android download interface
        Android.downloadContent(id, type, title, year, null, null);
        console.log('Called Android download interface successfully');
        this.showToast('Starting download analysis...');
        return;
      } catch (error) {
        console.error('Error using Android download interface:', error);
        this.showToast('Download failed: ' + error.message);
      }
    } else {
      console.log('Android download interface not available');
      this.showToast('Download only available in Android app');
    }
  }
  
  downloadTVEpisode(tvId, season, episode, showTitle, episodeTitle) {
    console.log(`Attempting to download: ${showTitle} S${season}E${episode} - ${episodeTitle}`);
    
    // Check if Android interface is available (running in WebView)
    if (typeof Android !== 'undefined' && typeof Android.downloadContent === 'function') {
      console.log('Using Android download interface for TV episode');
      
      try {
        // Get additional metadata
        const contentDetails = this.getCurrentContentDetails();
        const year = contentDetails?.year || '2023'; // Fallback year
        
        // Use the Android download interface with season and episode
        Android.downloadContent(tvId, 'tv', showTitle, year, season.toString(), episode.toString());
        console.log('Called Android download interface for TV episode successfully');
        this.showToast('Starting episode download analysis...');
        return;
      } catch (error) {
        console.error('Error using Android download interface for TV episode:', error);
        this.showToast('Download failed: ' + error.message);
      }
    } else {
      console.log('Android download interface not available');
      this.showToast('Download only available in Android app');
    }
  }
  
  showToast(message) {
    // Create a simple toast notification
    const toast = document.createElement('div');
    toast.textContent = message;
    toast.style.cssText = `
      position: fixed;
      top: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: var(--card-bg);
      color: var(--text-primary);
      padding: 1rem 2rem;
      border-radius: 8px;
      border: 1px solid var(--border-color);
      z-index: 10000;
      font-size: 0.9rem;
      max-width: 80%;
      text-align: center;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    `;
    
    document.body.appendChild(toast);
    
    // Remove toast after 3 seconds
    setTimeout(() => {
      if (toast.parentNode) {
        toast.parentNode.removeChild(toast);
      }
    }, 3000);
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
  
  showDownloads() {
    this.navigateToPage('downloads');
    this.renderDownloadsPage();
  }
  
  renderDownloadsPage() {
    const contentSections = document.getElementById('contentSections');
    
    contentSections.innerHTML = `
      <div class="downloads-page">
        <div class="content-section">
          <div class="section-header">
            <h2 class="section-title">💾 Downloads</h2>
            <button onclick="app.refreshDownloads()" style="background: var(--primary-orange); color: white; border: none; padding: 0.5rem 1rem; border-radius: 6px; font-size: 0.85rem; cursor: pointer;">
              🔄 Refresh
            </button>
          </div>
          
          <!-- Active Downloads -->
          <div id="activeDownloads" class="downloads-section">
            <h3 style="color: var(--text-primary); margin-bottom: 1rem; font-size: 1.1rem;">🔄 Active Downloads</h3>
            <div id="activeDownloadsList"></div>
          </div>
          
          <!-- Completed Downloads -->
          <div id="completedDownloads" class="downloads-section" style="margin-top: 2rem;">
            <h3 style="color: var(--text-primary); margin-bottom: 1rem; font-size: 1.1rem;">✓ Completed Downloads</h3>
            <div id="completedDownloadsList"></div>
          </div>
        </div>
      </div>
    `;
    
    this.loadDownloads();
  }
  
  loadDownloads() {
    if (typeof Android === 'undefined') {
      document.getElementById('activeDownloadsList').innerHTML = 
        '<p style="color: var(--text-secondary); text-align: center; padding: 2rem;">Downloads only available in Android app</p>';
      document.getElementById('completedDownloadsList').innerHTML = 
        '<p style="color: var(--text-secondary); text-align: center; padding: 2rem;">Downloads only available in Android app</p>';
      return;
    }
    
    try {
      // Load active downloads
      const activeDownloadsJson = Android.getActiveDownloads();
      const activeDownloads = JSON.parse(activeDownloadsJson);
      this.renderActiveDownloads(activeDownloads);
      
      // Load completed downloads
      const completedDownloadsJson = Android.getCompletedDownloads();
      const completedDownloads = JSON.parse(completedDownloadsJson);
      this.renderCompletedDownloads(completedDownloads);
      
    } catch (error) {
      console.error('Error loading downloads:', error);
      document.getElementById('activeDownloadsList').innerHTML = 
        '<p style="color: var(--text-secondary); text-align: center; padding: 2rem;">Error loading downloads</p>';
      document.getElementById('completedDownloadsList').innerHTML = 
        '<p style="color: var(--text-secondary); text-align: center; padding: 2rem;">Error loading downloads</p>';
    }
  }
  
  renderActiveDownloads(downloads) {
    const container = document.getElementById('activeDownloadsList');
    
    if (downloads.length === 0) {
      container.innerHTML = '<p style="color: var(--text-secondary); text-align: center; padding: 2rem;">No active downloads</p>';
      return;
    }
    
    container.innerHTML = downloads.map(download => `
      <div class="download-item active" data-download-id="${download.id}">
        <div class="download-info">
          <h4 class="download-title">${download.title}${download.season ? ` S${download.season}E${download.episode}` : ''}</h4>
          <p class="download-filename">${download.filename}</p>
          <div class="download-progress">
            <div class="progress-bar">
              <div class="progress-fill" style="width: ${download.progress}%"></div>
            </div>
            <span class="progress-text">${download.progress}% - ${download.status}</span>
          </div>
        </div>
        <div class="download-actions">
          <button onclick="app.cancelDownload('${download.id}')" class="cancel-btn">❌ Cancel</button>
        </div>
      </div>
    `).join('');
  }
  
  renderCompletedDownloads(downloads) {
    const container = document.getElementById('completedDownloadsList');
    
    if (downloads.length === 0) {
      container.innerHTML = '<p style="color: var(--text-secondary); text-align: center; padding: 2rem;">No completed downloads</p>';
      return;
    }
    
    container.innerHTML = downloads.map(download => `
      <div class="download-item completed">
        <div class="download-info">
          <h4 class="download-title">${download.title}${download.season ? ` S${download.season}E${download.episode}` : ''}</h4>
          <p class="download-filename">${download.filename}</p>
          <p class="download-status">✓ Download complete</p>
        </div>
        <div class="download-actions">
          <button onclick="app.playDownloadedFile('${download.filePath}')" class="play-btn">▶️ Play</button>
        </div>
      </div>
    `).join('');
  }
  
  refreshDownloads() {
    this.loadDownloads();
  }
  
  cancelDownload(downloadId) {
    if (typeof Android !== 'undefined' && typeof Android.cancelDownload === 'function') {
      Android.cancelDownload(downloadId);
      // Refresh after a short delay
      setTimeout(() => this.loadDownloads(), 1000);
    }
  }
  
  playDownloadedFile(filePath) {
    if (typeof Android !== 'undefined' && typeof Android.playDownloadedFile === 'function') {
      Android.playDownloadedFile(filePath);
    }
  }
  
  // Download event callbacks from Android
  onDownloadStarted(downloadId) {
    console.log('Download started:', downloadId);
    if (this.currentPage === 'downloads') {
      this.loadDownloads();
    }
  }
  
  onDownloadProgress(downloadId, progress) {
    // Update progress bar if downloads page is active
    if (this.currentPage === 'downloads') {
      const downloadItem = document.querySelector(`[data-download-id="${downloadId}"]`);
      if (downloadItem) {
        const progressFill = downloadItem.querySelector('.progress-fill');
        const progressText = downloadItem.querySelector('.progress-text');
        if (progressFill) progressFill.style.width = progress + '%';
        if (progressText) progressText.textContent = progress + '% - Downloading';
      }
    }
  }
  
  onDownloadCompleted(downloadId, filePath) {
    console.log('Download completed:', downloadId, filePath);
    this.showToast('Download completed!');
    if (this.currentPage === 'downloads') {
      setTimeout(() => this.loadDownloads(), 1000);
    }
  }
  
  onDownloadFailed(downloadId, error) {
    console.log('Download failed:', downloadId, error);
    this.showToast('Download failed: ' + error);
    if (this.currentPage === 'downloads') {
      setTimeout(() => this.loadDownloads(), 1000);
    }
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
function showDownloads() { app.showDownloads(); }
function exploreContent() { app.showMovies(); }

// Initialize app when DOM is loaded
let app;
document.addEventListener('DOMContentLoaded', () => {
  app = new MTVApp();
  window.app = app; // Make app available globally
  console.log('App initialized and available globally');
});
