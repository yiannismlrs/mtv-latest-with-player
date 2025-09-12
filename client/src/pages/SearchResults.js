import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import ContentCard from '../components/ContentCard';
import { searchAPI } from '../utils/api';

const VIDSRC_EMBED = process.env.REACT_APP_VIDSRC_EMBED_URL || 'https://vidsrc.to/embed';

export default function SearchResults() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const q = searchParams.get('query') || searchParams.get('q') || '';
  const [tab, setTab] = useState('all'); // all | movie | tv
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let type = tab === 'all' ? 'multi' : tab;
    setLoading(true);
    (async () => {
      try {
        const res = await searchAPI.query(q, type, 1);
        setResults(res?.data?.results || []);
      } finally {
        setLoading(false);
      }
    })();
  }, [q, tab]);

  const buildStreamingLinks = (item) => {
    const isMovie = !!item.title;
    const path = isMovie ? 'movie' : 'tv';
    return [{
      provider: 'VidSrc',
      quality: 'HD',
      type: 'embed',
      url: `${VIDSRC_EMBED}/${path}/${item.id}`,
    }];
  };

  const onOpen = (item) => navigate(`/${item.title ? 'movie' : 'tv'}/${item.id}`);
  const onWatch = (item) =>
    navigate('/watch', { state: { content: item, type: item.title ? 'movie' : 'tv', streamingLinks: buildStreamingLinks(item) } });

  if (!q) {
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
        <h1>Results for “{q}”</h1>
      </div>

      <div className="search-tabs">
        {['all', 'movie', 'tv'].map(t => (
          <button key={t}
                  className={`tab-btn ${tab === t ? 'active' : ''}`}
                  onClick={() => setTab(t)}>
            {t === 'all' ? 'All' : t === 'movie' ? 'Movies' : 'TV Series'}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="loading"><div className="spinner" /></div>
      ) : results.length === 0 ? (
        <div className="no-results">
          <h2>No results</h2>
          <p>Try another keyword.</p>
        </div>
      ) : (
        <div className="content-grid">
          {results
            // filter out people if using multi
            .filter(r => r.media_type !== 'person')
            .map(item => (
              <ContentCard
                key={`${item.media_type || (item.title ? 'movie' : 'tv')}-${item.id}`}
                item={item}
                type={item.title ? 'movie' : 'tv'}
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
