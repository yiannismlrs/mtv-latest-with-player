import React, { useState, useEffect } from 'react';
import { moviesAPI, tvShowsAPI } from '../utils/api';
import ContentCard from './ContentCard';

export default function DetailTabs({ content, type }) {
  const [active, setActive] = useState('overview');
  const [cast, setCast] = useState([]);
  const [related, setRelated] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (content?.id && (active === 'casts' || active === 'related')) {
      setLoading(true);
      (async () => {
        try {
          if (active === 'casts') {
            const api = type === 'movie' ? moviesAPI : tvShowsAPI;
            const res = await api.getCredits(content.id);
            setCast(res?.data?.cast?.slice(0, 20) || []);
          } else if (active === 'related') {
            const api = type === 'movie' ? moviesAPI : tvShowsAPI;
            const res = await api.getRelated(content.id);
            setRelated(res?.data?.results?.slice(0, 12) || []);
          }
        } catch (error) {
          console.error('Error fetching tab data:', error);
        } finally {
          setLoading(false);
        }
      })();
    }
  }, [content?.id, type, active]);

  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'casts', label: 'Casts' },
    { id: 'related', label: 'Related' },
  ];

  const genres =
    typeof content?.genres === 'string'
      ? JSON.parse(content.genres)
      : content?.genres || [];

  return (
    <div className="detail-tabs">
      <div className="tabs-header">
        {tabs.map(t => (
          <button
            key={t.id}
            className={`tab-button ${active === t.id ? 'active' : ''}`}
            onClick={() => setActive(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Overview */}
      {active === 'overview' && (
        <div className="tab-content">
          <p className="overview-text">{content?.overview}</p>

          <div className="detail-section">
            <h3>Genre</h3>
            <p className="genre-text">
              {genres.length ? genres.map(g => g.name).join(', ') : 'N/A'}
            </p>
          </div>
        </div>
      )}

      {/* Casts (placeholder—wire to your API when ready) */}
      {active === 'casts' && (
        <div className="tab-content">
          {loading ? (
            <div className="loading"><div className="spinner" /></div>
          ) : cast.length > 0 ? (
            <div className="cast-grid">
              {cast.map(person => (
                <div key={person.id} className="cast-member">
                  <img 
                    src={person.profile_path ? `https://image.tmdb.org/t/p/w185${person.profile_path}` : '/placeholder-person.jpg'}
                    alt={person.name}
                    className="cast-photo"
                  />
                  <div className="cast-info">
                    <div className="cast-name">{person.name}</div>
                    <div className="cast-character">{person.character}</div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p>No cast information available.</p>
          )}
        </div>
      )}

      {/* Related (placeholder) */}
      {active === 'related' && (
        <div className="tab-content">
          {loading ? (
            <div className="loading"><div className="spinner" /></div>
          ) : related.length > 0 ? (
            <div className="content-grid">
              {related.map(item => (
                <ContentCard
                  key={item.id}
                  item={item}
                  type={item.title ? 'movie' : 'tv'}
                  onClick={() => {}}
                  onWatch={() => {}}
                  onDownload={() => {}}
                />
              ))}
            </div>
          ) : (
            <p>No related content found.</p>
          )}
        </div>
      )}
    </div>
  );
}
