import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Play, Download, Share, Star, Calendar, Clock,
  ArrowLeft, Cast as CastIcon, Heart, Bookmark, Youtube
} from 'lucide-react';
import { tvShowsAPI, getImageUrl, getBackdropUrl, watchlistAPI, openInSPlayer, getSPlayerUrl } from '../utils/api';
import DetailTabs from '../components/DetailTabs';

export default function TVShowPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [show, setShow] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isInWatchlist, setIsInWatchlist] = useState(false);

  const fetchDetails = useCallback(async () => {
    try {
      const res = await tvShowsAPI.getDetails(id);
      if (res?.data?.success) {
        setShow(res.data.tvshow);
        setIsInWatchlist(watchlistAPI.isInWatchlist(id, 'tv'));
      }
    } catch (e) {
      console.error('Error fetching TV details:', e);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { if (id) fetchDetails(); }, [id, fetchDetails]);

  const hasStreamingLinks = !!show?.streaming_links?.length;
  const hasDownloadLinks = !!show?.download_links?.length;
  const year = show?.first_air_date ? new Date(show.first_air_date).getFullYear() : '';
  const rating = typeof show?.vote_average === 'number' ? show.vote_average.toFixed(1) : 'N/A';

  const handleWatch = () => {
    if (show) {
      const url = getSPlayerUrl(show, 'tv');
      openInSPlayer(url, show.name);
    }
  };

  const handleAddToWatchlist = () => {
    if (show) {
      if (isInWatchlist) {
        watchlistAPI.removeFromWatchlist(show.id, 'tv');
        setIsInWatchlist(false);
      } else {
        watchlistAPI.addToWatchlist(show, 'tv');
        setIsInWatchlist(true);
      }
    }
  };

  const handleShare = () => {
    if (navigator.share && show) {
      navigator.share({
        title: show.name,
        text: `Check out ${show.name} on MTV`,
        url: window.location.href
      });
    } else {
      navigator.clipboard.writeText(window.location.href);
      alert('Link copied to clipboard!');
    }
  };

  const handleTrailer = () => {
    const query = encodeURIComponent(`${show.name} ${year} trailer`);
    window.open(`https://www.youtube.com/results?search_query=${query}`, '_blank');
  };

  if (loading) return <div className="loading"><div className="spinner" /></div>;
  if (!show) return <div className="error-message"><h2>TV Show not found</h2></div>;

  return (
    <div className="detail-page">
      <div className="top-actions">
        <button className="top-btn" aria-label="Back" onClick={() => navigate(-1)}>
          <ArrowLeft />
        </button>
        <button className="top-btn" aria-label="Cast"><CastIcon /></button>
      </div>

      <section className="detail-hero">
        <div
          className="hero-bg"
          style={{ backgroundImage: `url(${getBackdropUrl(show.backdrop_path)})` }}
        />
        <div className="detail-hero-content">
          <div className="detail-poster">
            <img className="detail-poster-image" src={getImageUrl(show.poster_path)} alt={show.name} />
          </div>
          <div className="detail-info">
            <h1 className="detail-title">{show.name}</h1>
            <div className="detail-meta">
              <Calendar size={16} /><span className="year">{year}</span>
              {show.episode_run_time?.[0] ? (
                <>
                  <span>•</span><Clock size={16} /><span>{show.episode_run_time[0]} min</span>
                </>
              ) : null}
              <span>•</span><Star className="star" size={16} /><span className="rating">{rating}</span>
            </div>
          </div>
        </div>
      </section>

      <div className="detail-content">
        <div className="primary-actions">
          {hasStreamingLinks && (
            <button className="watch-button" onClick={handleWatch}>
              <Play size={20} /> Watch
            </button>
          )}
          {hasDownloadLinks && (
            <button className="download-button" onClick={() => alert('Download feature coming soon!')}>
              <Download size={20} /> Download
            </button>
          )}
        </div>

        <div className="secondary-actions">
          <button className="action-icon" onClick={handleAddToWatchlist}>
            <Bookmark size={18} fill={isInWatchlist ? 'currentColor' : 'none'} />
            <span>{isInWatchlist ? 'Remove' : 'Add List'}</span>
          </button>
          <button className="action-icon" onClick={handleTrailer}>
            <Youtube size={18} /><span>Trailer</span>
          </button>
          <button className="action-icon" onClick={handleShare}>
            <Share size={18} /><span>Share</span>
          </button>
          <button className="action-icon"><Heart size={18} /><span>Favorite</span></button>
        </div>

        <DetailTabs content={show} type="tv" />

        {hasStreamingLinks && (
          <div className="download-section">
            <h2>Watch Options</h2>
            <div className="download-links">
              <button className="download-link" onClick={handleWatch}>
                <Play size={18} /><span>SPlayer</span>
                <span style={{ marginLeft: 'auto' }}>HD</span>
              </button>
            </div>
          </div>
        )}

        {hasDownloadLinks && (
          <div className="download-section">
            <h2>Download Options</h2>
            <div className="download-links">
              {show.download_links.map((link, i) => (
                <a key={i} className="download-link" href={link.url} target="_blank" rel="noreferrer">
                  <Download size={18} /><span>{(link.format || '').toUpperCase()}</span>
                  <span style={{ marginLeft: 'auto' }}>{link.quality}</span>
                </a>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
