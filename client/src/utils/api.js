// client/src/utils/api.js
import axios from 'axios';

const TMDB_KEY = process.env.REACT_APP_TMDB_API_KEY;
const SPLAYER_WEBSITE = 'https://splayer.org';
const SPLAYER_SCHEME = 'splayer://';

const tmdb = axios.create({
  baseURL: 'https://api.themoviedb.org/3',
  params: { api_key: TMDB_KEY },
});

// helpers
export const getImageUrl = (p) => (p ? `https://image.tmdb.org/t/p/w500${p}` : '');
export const getBackdropUrl = (p) => (p ? `https://image.tmdb.org/t/p/w1280${p}` : '');

// SPlayer integration helpers
export const openInSPlayer = (url, title) => {
  const splayerUrl = `${SPLAYER_SCHEME}play?url=${encodeURIComponent(url)}&title=${encodeURIComponent(title)}`;
  
  // Try to open in SPlayer app
  window.location.href = splayerUrl;
  
  // Fallback: redirect to SPlayer website after a delay
  setTimeout(() => {
    window.open(SPLAYER_WEBSITE, '_blank');
  }, 2000);
};

export const getSPlayerUrl = (item, type) => {
  // Generate streaming URL for SPlayer
  const baseUrl = 'https://vidsrc.to/embed';
  const path = type === 'movie' ? 'movie' : 'tv';
  return `${baseUrl}/${path}/${item.id}`;
};

// shape adapter so your pages don't need changes
const withMovieExtras = (m) => ({
  ...m,
  streaming_links: m?.id ? [{ provider: 'SPlayer', quality: 'HD', type: 'app', url: getSPlayerUrl(m, 'movie') }] : [],
  download_links: [],
});
const withTVExtras = (s) => ({
  ...s,
  streaming_links: s?.id ? [{ provider: 'SPlayer', quality: 'HD', type: 'app', url: getSPlayerUrl(s, 'tv') }] : [],
  download_links: [],
});

export const moviesAPI = {
  async getDetails(id) {
    const { data } = await tmdb.get(`/movie/${id}`);
    return { data: { success: true, movie: withMovieExtras(data) } };
  },
  async getCredits(id) {
    const { data } = await tmdb.get(`/movie/${id}/credits`);
    return { data };
  },
  async getRelated(id, page = 1) {
    const { data } = await tmdb.get(`/movie/${id}/recommendations`, { params: { page } });
    return { data: { success: true, results: data.results } };
  },
  async getPopular(page = 1) {
    const { data } = await tmdb.get('/movie/popular', { params: { page } });
    return { data: { success: true, results: data.results } };
  },
  async getTrending(page = 1) {
    const { data } = await tmdb.get('/trending/movie/day', { params: { page } });
    return { data: { success: true, results: data.results } };
  },
};

export const tvShowsAPI = {
  async getDetails(id) {
    const { data } = await tmdb.get(`/tv/${id}`);
    return { data: { success: true, tvshow: withTVExtras(data) } };
  },
  async getCredits(id) {
    const { data } = await tmdb.get(`/tv/${id}/credits`);
    return { data };
  },
  async getRelated(id, page = 1) {
    const { data } = await tmdb.get(`/tv/${id}/recommendations`, { params: { page } });
    return { data: { success: true, results: data.results } };
  },
  async getPopular(page = 1) {
    const { data } = await tmdb.get('/tv/popular', { params: { page } });
    return { data: { success: true, results: data.results } };
  },
  async getTrending(page = 1) {
    const { data } = await tmdb.get('/trending/tv/day', { params: { page } });
    return { data: { success: true, results: data.results } };
  },
};

export const searchAPI = {
  async query(q, type = 'multi', page = 1) {
    const path = type === 'movie' ? '/search/movie' : type === 'tv' ? '/search/tv' : '/search/multi';
    const { data } = await tmdb.get(path, { params: { query: q, page } });
    return { data: { success: true, results: data.results } };
  },
};

// Watchlist management (using localStorage for now)
export const watchlistAPI = {
  getWatchlist() {
    const watchlist = localStorage.getItem('mtv_watchlist');
    return watchlist ? JSON.parse(watchlist) : [];
  },
  
  addToWatchlist(item, type) {
    const watchlist = this.getWatchlist();
    const newItem = { ...item, type, addedAt: Date.now() };
    const exists = watchlist.find(w => w.id === item.id && w.type === type);
    
    if (!exists) {
      watchlist.unshift(newItem);
      localStorage.setItem('mtv_watchlist', JSON.stringify(watchlist));
    }
    return watchlist;
  },
  
  removeFromWatchlist(id, type) {
    const watchlist = this.getWatchlist();
    const filtered = watchlist.filter(w => !(w.id === id && w.type === type));
    localStorage.setItem('mtv_watchlist', JSON.stringify(filtered));
    return filtered;
  },
  
  isInWatchlist(id, type) {
    const watchlist = this.getWatchlist();
    return watchlist.some(w => w.id === id && w.type === type);
  }
};