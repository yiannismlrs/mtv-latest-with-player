// MTV Streaming App - Storage Module
class StorageService {
  constructor() {
    this.prefix = CONFIG.APP.STORAGE_PREFIX;
  }

  // Generic storage methods
  set(key, value) {
    try {
      const prefixedKey = `${this.prefix}${key}`;
      const serializedValue = JSON.stringify({
        data: value,
        timestamp: Date.now()
      });
      localStorage.setItem(prefixedKey, serializedValue);
      return true;
    } catch (error) {
      console.error('Storage set error:', error);
      return false;
    }
  }

  get(key, defaultValue = null) {
    try {
      const prefixedKey = `${this.prefix}${key}`;
      const item = localStorage.getItem(prefixedKey);
      if (!item) return defaultValue;
      
      const parsed = JSON.parse(item);
      return parsed.data;
    } catch (error) {
      console.error('Storage get error:', error);
      return defaultValue;
    }
  }

  remove(key) {
    try {
      const prefixedKey = `${this.prefix}${key}`;
      localStorage.removeItem(prefixedKey);
      return true;
    } catch (error) {
      console.error('Storage remove error:', error);
      return false;
    }
  }

  clear() {
    try {
      const keys = Object.keys(localStorage);
      keys.forEach(key => {
        if (key.startsWith(this.prefix)) {
          localStorage.removeItem(key);
        }
      });
      return true;
    } catch (error) {
      console.error('Storage clear error:', error);
      return false;
    }
  }

  // Watchlist specific methods
  getWatchlist() {
    return this.get('watchlist', []);
  }

  saveWatchlist(watchlist) {
    return this.set('watchlist', watchlist);
  }

  addToWatchlist(item) {
    const watchlist = this.getWatchlist();
    const exists = watchlist.some(w => w.id === item.id && w.type === item.type);
    
    if (!exists) {
      watchlist.unshift({
        ...item,
        addedAt: Date.now()
      });
      this.saveWatchlist(watchlist);
      return true;
    }
    return false;
  }

  removeFromWatchlist(id, type) {
    const watchlist = this.getWatchlist();
    const filtered = watchlist.filter(item => !(item.id === id && item.type === type));
    
    if (filtered.length !== watchlist.length) {
      this.saveWatchlist(filtered);
      return true;
    }
    return false;
  }

  isInWatchlist(id, type) {
    const watchlist = this.getWatchlist();
    return watchlist.some(item => item.id === id && item.type === type);
  }

  // Search history methods
  getSearchHistory() {
    return this.get('search_history', []);
  }

  addToSearchHistory(query) {
    if (!query.trim()) return;
    
    const history = this.getSearchHistory();
    const filtered = history.filter(item => item.query !== query);
    
    filtered.unshift({
      query,
      timestamp: Date.now()
    });
    
    // Keep only last 20 searches
    const trimmed = filtered.slice(0, 20);
    this.set('search_history', trimmed);
  }

  clearSearchHistory() {
    return this.remove('search_history');
  }

  // User preferences methods
  getPreferences() {
    return this.get('preferences', {
      theme: 'dark',
      language: 'en',
      autoplay: true,
      notifications: true
    });
  }

  savePreferences(preferences) {
    return this.set('preferences', preferences);
  }

  updatePreference(key, value) {
    const preferences = this.getPreferences();
    preferences[key] = value;
    return this.savePreferences(preferences);
  }

  // Recently viewed methods
  getRecentlyViewed() {
    return this.get('recently_viewed', []);
  }

  addToRecentlyViewed(item) {
    const recent = this.getRecentlyViewed();
    const filtered = recent.filter(r => !(r.id === item.id && r.type === item.type));
    
    filtered.unshift({
      ...item,
      viewedAt: Date.now()
    });
    
    // Keep only last 50 items
    const trimmed = filtered.slice(0, 50);
    this.set('recently_viewed', trimmed);
  }

  clearRecentlyViewed() {
    return this.remove('recently_viewed');
  }
}

// Create global storage instance
const storageService = new StorageService();