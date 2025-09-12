import React, { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, Settings, ExternalLink } from 'lucide-react';

export default function WatchPage() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { content, type } = state || {};

  if (!content) {
    return (
      <div className="main-content">
        <button className="back-btn" onClick={() => navigate(-1)}><ArrowLeft /> Back</button>
        <div className="error-message">
          <h2>SPlayer Integration</h2>
          <p>This page is no longer used. Content opens directly in SPlayer app.</p>
          <button className="btn-primary" onClick={() => navigate('/')}>
            Go Home
          </button>
        </div>
      </div>
    );
  }

  const openSPlayerWebsite = () => {
    window.open('https://splayer.org', '_blank');
  };

  return (
    <div className="watch-page">
      <div className="watch-header">
        <button className="back-btn" onClick={() => navigate(-1)}><ArrowLeft /> Back</button>
        <h1 className="watch-title">SPlayer Integration</h1>
        <div className="watch-controls">
          <button className="control-btn" onClick={openSPlayerWebsite} title="Get SPlayer">
            <ExternalLink size={18}/>
          </button>
        </div>
      </div>

      <div className="main-content" style={{ textAlign: 'center', padding: '2rem' }}>
        <h2>Content opens in SPlayer</h2>
        <p>Movies and TV shows now open directly in the SPlayer application for the best viewing experience.</p>
        
        <div style={{ margin: '2rem 0' }}>
          <button className="btn-primary" onClick={openSPlayerWebsite}>
            <ExternalLink size={18} /> Download SPlayer
          </button>
        </div>
        
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          If you don't have SPlayer installed, clicking "Watch Now" will redirect you to download it.
        </p>
      </div>
    </div>
  );
}
