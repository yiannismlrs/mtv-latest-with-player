// client/src/utils/api.js
import axios from 'axios';

const MODE = process.env.REACT_APP_API_MODE || 'direct';
const TMDB_KEY = process.env.REACT_APP_TMDB_API_KEY;
const VIDSRC_EMBED = process.env.REACT_APP_VIDSRC_EMBED_URL || 'https://vidsrc.to/embed';

const tmdb = axios.create({
  baseURL: 'https://api.themoviedb.org/3',
  params: { api_key: TMDB_KEY },
});

// helpers
export const getImageUrl = (p) => (p ? `https://image.tmdb.org/t/p/w500${p}` : '');
export const getBackdropUrl = (p) => (p ? `https://image.tmdb.org/t/p/w1280${p}` : '');

// shape adapter so your pages don’t need changes
const withMovieExtras = (m) => ({
  ...m,
  streaming_links: m?.id ? [{ provider: 'VidSrc', quality: 'HD', type: 'embed', url: `${VIDSRC_EMBED}/movie/${m.id}` }] : [],
  download_links: [],
});
const withTVExtras = (s) => ({
  ...s,
  streaming_links: s?.id ? [{ provider: 'VidSrc', quality: 'HD', type: 'embed', url: `${VIDSRC_EMBED}/tv/${s.id}` }] : [],
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
    return { data };
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
    return { data };
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
