import React, { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, Settings } from 'lucide-react';

const VIDSRC_EMBED = process.env.REACT_APP_VIDSRC_EMBED_URL || 'https://vidsrc.to/embed';

export default function WatchPage() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { content, type, streamingLinks } = state || {};

  const src = useMemo(() => {
    const link = streamingLinks?.[0]?.url;
    if (link) return link;
    if (content?.id && (type === 'movie' || type === 'tv')) {
      return `${VIDSRC_EMBED}/${type}/${content.id}`;
    }
    return '';
  }, [content, type, streamingLinks]);

  if (!src) {
    return (
      <div className="main-content">
        <button className="back-btn" onClick={() => navigate(-1)}><ArrowLeft /> Back</button>
        <div className="error-message">
          <h2>Nothing to play</h2>
          <p>Open a movie or TV show first and tap Watch.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="watch-page">
      <div className="watch-header">
        <button className="back-btn" onClick={() => navigate(-1)}><ArrowLeft /> Back</button>
        <h1 className="watch-title">{content?.title || content?.name || 'Player'}</h1>
        <div className="watch-controls">
          <button className="control-btn" title="Settings"><Settings size={18}/></button>
        </div>
      </div>

      <div className="video-player">
        <div className="player-container">
          <iframe
            className="player-iframe"
            src={src}
            allowFullScreen
            title="Player"
          />
        </div>
      </div>
    </div>
  );
}
