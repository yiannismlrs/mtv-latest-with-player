import React from 'react';
import { Play, Star, Download } from 'lucide-react';
import { getImageUrl } from '../utils/api';

const ContentCard = ({ item, type, onClick, onWatch, onDownload }) => {
  const title = item.title || item.name;
  const releaseDate = item.release_date || item.first_air_date;
  const year = releaseDate ? new Date(releaseDate).getFullYear() : '';
  const rating = item.vote_average ? item.vote_average.toFixed(1) : 'N/A';
  
  const hasStreamingLinks = item.streaming_links && item.streaming_links.length > 0;
  const hasDownloadLinks = item.download_links && item.download_links.length > 0;

  const handleWatchClick = (e) => {
    e.stopPropagation();
    if (onWatch && hasStreamingLinks) {
      onWatch(item, type);
    }
  };

  const handleDownloadClick = (e) => {
    e.stopPropagation();
    if (onDownload && hasDownloadLinks) {
      onDownload(item, type);
    }
  };

  return (
    <div className="content-card" onClick={() => onClick && onClick(item, type)}>
      <div 
        className="card-poster"
        style={{
          backgroundImage: `url(${getImageUrl(item.poster_path)})`
        }}
      >
        {hasStreamingLinks && (
          <div className="play-overlay" onClick={handleWatchClick}>
            <Play size={24} fill="white" />
          </div>
        )}
        
        {(hasStreamingLinks || hasDownloadLinks) && (
          <div className="card-actions">
            {hasStreamingLinks && (
              <button 
                className="card-action-btn watch-btn"
                onClick={handleWatchClick}
                title="Watch Now"
              >
                <Play size={16} />
              </button>
            )}
            {hasDownloadLinks && (
              <button 
                className="card-action-btn download-btn"
                onClick={handleDownloadClick}
                title="Download"
              >
                <Download size={16} />
              </button>
            )}
          </div>
        )}
      </div>
      
      <div className="card-content">
        <div className="card-title" title={title}>
          {title}
        </div>
        <div className="card-meta">
          <span className="card-year">{year}</span>
          <div className="rating">
            <Star size={12} className="star" fill="currentColor" />
            <span>{rating}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ContentCard;
