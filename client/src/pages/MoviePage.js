import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Play, Download, Share, Star, Calendar, Clock,
  ArrowLeft, Cast as CastIcon, Heart, Bookmark, Youtube
} from 'lucide-react';
import { moviesAPI, getImageUrl, getBackdropUrl, watchlistAPI, openInSPlayer, getSPlayerUrl } from '../utils/api';
import DetailTabs from '../components/DetailTabs';

export default function MoviePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [movie, setMovie] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isInWatchlist, setIsInWatchlist] = useState(false);

  const fetchMovieDetails = useCallback(async () => {
    try {
      const res = await moviesAPI.getDetails(id);
      if (res?.data?.success) {
        setMovie(res.data.movie);
        setIsInWatchlist(watchlistAPI.isInWatchlist(id, 'movie'));
      }
    } catch (e) {
      console.error('Error fetching movie details:', e);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (id) fetchMovieDetails();
  }, [id, fetchMovieDetails]);

  const hasStreamingLinks = !!movie?.streaming_links?.length;
  const hasDownloadLinks = !!movie?.download_links?.length;
  const releaseYear = movie?.release_date ? new Date(movie.release_date).getFullYear() : '';
  const rating = typeof movie?.vote_average === 'number' ? movie.vote_average.toFixed(1) : 'N/A';

  const handleWatch = () => {
    if (movie) {
      const url = getSPlayerUrl(movie, 'movie');
      openInSPlayer(url, movie.title);
    }
  };

  const handleAddToWatchlist = () => {
    if (movie) {
      if (isInWatchlist) {
        watchlistAPI.removeFromWatchlist(movie.id, 'movie');
        setIsInWatchlist(false);
      } else {
        watchlistAPI.addToWatchlist(movie, 'movie');
        setIsInWatchlist(true);
      }
    }
  };

  const handleShare = () => {
    if (navigator.share && movie) {
      navigator.share({
        title: movie.title,
        text: `Check out ${movie.title} on MTV`,
        url: window.location.href
      });
    } else {
      // Fallback: copy to clipboard
      navigator.clipboard.writeText(window.location.href);
      alert('Link copied to clipboard!');
    }
  };

  const handleTrailer = () => {
    // Search for trailer on YouTube
    const query = encodeURIComponent(`${movie.title} ${releaseYear} trailer`);
    window.open(`https://www.youtube.com/results?search_query=${query}`, '_blank');
  };

  if (loading) {
    return (
      <div className="loading"><div className="spinner" /></div>
    );
  }

  if (!movie) {
    return (
      <div className="error-message">
        <h2>Movie not found</h2>
        <p>It may have been removed or is temporarily unavailable.</p>
      </div>
    );
  }

  return (
    <div className="detail-page">
      {/* Top overlay actions */}
      <div className="top-actions">
        <button className="top-btn" aria-label="Back" onClick={() => navigate(-1)}>
          <ArrowLeft />
        </button>
        <button className="top-btn" aria-label="Cast">
          <CastIcon />
        </button>
      </div>

      {/* Hero */}
      <section className="detail-hero">
        <div
          className="hero-bg"
          style={{ backgroundImage: `url(${getBackdropUrl(movie.backdrop_path)})` }}
        />
        <div className="detail-hero-content">
          <div className="detail-poster">
            <img
              className="detail-poster-image"
              src={getImageUrl(movie.poster_path)}
              alt={movie.title}
            />
          </div>

          <div className="detail-info">
            <h1 className="detail-title">{movie.title}</h1>
            <div className="detail-meta">
              <Calendar size={16} /><span className="year">{releaseYear}</span>
              {movie.runtime ? (<><span>•</span><Clock size={16} /><span>{movie.runtime} min</span></>) : null}
              <span>•</span><Star className="star" size={16} />
              <span className="rating">{rating}</span>
            </div>
          </div>
        </div>
      </section>

      {/* Primary Actions */}
      <div className="detail-content">
        <div className="primary-actions">
          {hasStreamingLinks && (
            <button className="watch-button" onClick={() => onWatch()}>
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

        {/* Secondary icons */}
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

        {/* Tabs */}
        <DetailTabs content={movie} type="movie" />

        {/* Streaming options list (compact, mobile) */}
        {hasStreamingLinks && (
          <div className="download-section">
            <h2>Watch Options</h2>
            <div className="download-links">
              <button className="download-link" onClick={handleWatch}>
                <Play size={18} />
                <span>SPlayer</span>
                <span style={{ marginLeft: 'auto' }}>HD</span>
              </button>
            </div>
          </div>
        )}

        {/* Download options */}
        {hasDownloadLinks && (
          <div className="download-section">
            <h2>Download Options</h2>
            <div className="download-links">
              {movie.download_links.map((link, i) => (
                <a
                  key={i}
                  className="download-link"
                  href={link.url}
                  target="_blank"
                  rel="noreferrer"
                >
                  <Download size={18} />
                  <span>{(link.format || '').toUpperCase()}</span>
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
