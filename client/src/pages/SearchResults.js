import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import ContentCard from '../components/ContentCard';
import { searchAPI, moviesAPI, tvShowsAPI, watchlistAPI, openInSPlayer, getSPlayerUrl } from '../utils/api';

export default function SearchResults() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();

  const q = searchParams.get('query') || searchParams.get('q') || '';
  
  // Determine the type from the URL path
  const getTypeFromPath = () => {
    if (location.pathname === '/movies') return 'movie';
    if (location.pathname === '/tv-shows') return 'tv';
    if (location.pathname === '/watchlist') return 'watchlist';
    return 'all';
  };
  
  const [tab, setTab] = useState(getTypeFromPath());
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    (async () => {
      try {
        let data = [];
        
        if (tab === 'watchlist') {
          // Get watchlist from localStorage
          data = watchlistAPI.getWatchlist();
        } else if (q) {
          // Search query
          const type = tab === 'all' ? 'multi' : tab;
          const res = await searchAPI.query(q, type, 1);
          data = res?.data?.results || [];
        } else {
          // Browse by category
          if (tab === 'movie') {
            const res = await moviesAPI.getPopular(1);
            data = res?.data?.results || [];
          } else if (tab === 'tv') {
            const res = await tvShowsAPI.getPopular(1);
            data = res?.data?.results || [];
          }
        }
        
        setResults(data);
      } finally {
        setLoading(false);
      }
    })();
  }, [q, tab, location.pathname]);

  const onOpen = (item) => navigate(`/${item.title ? 'movie' : 'tv'}/${item.id}`);
  
  const onWatch = (item) => {
    const type = item.title ? 'movie' : 'tv';
    const url = getSPlayerUrl(item, type);
    const title = item.title || item.name;
    openInSPlayer(url, title);
  };

  const getPageTitle = () => {
    if (tab === 'watchlist') return 'My Watchlist';
    if (tab === 'movie') return q ? `Movie Results for "${q}"` : 'Popular Movies';
    if (tab === 'tv') return q ? `TV Results for "${q}"` : 'Popular TV Shows';
    return q ? `Results for "${q}"` : 'Browse Content';
  };

  const shouldShowTabs = () => {
    return q || tab === 'watchlist';
  };

  if (!q && tab !== 'movie' && tab !== 'tv' && tab !== 'watchlist') {
    return (
      <div className="main-content search-results">
        <div className="search-empty">
          <h2>Search something…</h2>
          <p>Try a movie or TV title.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="main-content search-results">
      <div className="search-header">
        <h1>{getPageTitle()}</h1>
      </div>

      {shouldShowTabs() && (
        <div className="search-tabs">
          {tab === 'watchlist' ? (
            <div className="tab-btn active">My Watchlist</div>
          ) : (
            ['all', 'movie', 'tv'].map(t => (
              <button key={t}
                      className={`tab-btn ${tab === t ? 'active' : ''}`}
                      onClick={() => setTab(t)}>
                {t === 'all' ? 'All' : t === 'movie' ? 'Movies' : 'TV Series'}
              </button>
            ))
          )}
        </div>
      )}

      {loading ? (
        <div className="loading"><div className="spinner" /></div>
      ) : results.length === 0 ? (
        <div className="no-results">
          <h2>{tab === 'watchlist' ? 'Your watchlist is empty' : 'No results'}</h2>
          <p>{tab === 'watchlist' ? 'Add movies and TV shows to your watchlist.' : 'Try another keyword.'}</p>
        </div>
      ) : (
        <div className="content-grid">
          {results
            // filter out people if using multi
            .filter(r => !r.media_type || r.media_type !== 'person')
            .map(item => (
              <ContentCard
                key={`${item.type || item.media_type || (item.title ? 'movie' : 'tv')}-${item.id}`}
                item={item}
                type={item.type || (item.title ? 'movie' : 'tv')}
                onClick={(_, type) => onOpen(item)}
                onWatch={(_, type) => onWatch(item)}
                onDownload={() => {}}
              />
            ))}
        </div>
      )}
    </div>
  );
}
