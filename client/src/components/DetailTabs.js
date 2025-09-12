import React, { useState } from 'react';

export default function DetailTabs({ content, type }) {
  const [active, setActive] = useState('overview');

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
          <p>Cast information will appear here.</p>
        </div>
      )}

      {/* Related (placeholder) */}
      {active === 'related' && (
        <div className="tab-content">
          <p>Related titles will be shown here.</p>
        </div>
      )}
    </div>
  );
}
