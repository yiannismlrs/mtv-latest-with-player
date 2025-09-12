// client/src/pages/HomePage.js
import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Play, TrendingUp } from 'lucide-react';
import ContentCard from '../components/ContentCard';
import { moviesAPI, tvShowsAPI, getBackdropUrl } from '../utils/api';

const VIDSRC_EMBED = process.env.REACT_APP_VIDSRC_EMBED_URL || 'https://vidsrc.to/embed';

export default function HomePage() {
  const [trendingMovies, setTrendingMovies] = useState([]);
  const [trendingTVShows, setTrendingTVShows] = useState([]);
  const [popularMovies, setPopularMovies] = useState([]);
  const [popularTVShows, setPopularTVShows] = useState([]);
  const [featuredContent, setFeaturedContent] = useState(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    (async () => {
      try {
        const [tm, tt, pm, pt] = await Promise.all([
          moviesAPI.getTrending(1),
          tvShowsAPI.getTrending(1),
          moviesAPI.getPopular(1),
          tvShowsAPI.getPopular(1),
        ]);

        const tMovies = tm?.data?.results || [];
        const tTV     = tt?.data?.results || [];
        setTrendingMovies(tMovies);
        setTrendingTVShows(tTV);
        setPopularMovies(pm?.data?.results || []);
        setPopularTVShows(pt?.data?.results || []);

        // pick a hero (first trending movie, else first trending TV)
        setFeaturedContent(tMovies[0] || tTV[0] || null);
      } catch (err) {
        console.error('Home fetch error:', err);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const buildStreamingLinks = (item, type) => {
    if (item?.streaming_links?.length) return item.streaming_links;
    const path = type === 'movie' ? 'movie' : 'tv';
    return [{
      provider: 'VidSrc',
      quality: 'HD',
      type: 'embed',
      url: `${VIDSRC_EMBED}/${path}/${item.id}`,
    }];
  };

  const handleContentClick = (item, type) => {
    navigate(`${type === 'movie' ? '/movie' : '/tv'}/${item.id}`);
  };

  const handleWatchClick = (item, type) => {
    navigate('/watch', {
      state: {
        content: item,
        type,
        streamingLinks: buildStreamingLinks(item, type),
      },
    });
  };

  const handleFeaturedWatch = () => {
    if (!featuredContent) return;
    const type = featuredContent.title ? 'movie' : 'tv';
    handleWatchClick(featuredContent, type);
  };

  const handleDownloadClick = (item) => {
    // Placeholder for a future download modal
    console.log('Download options:', item.download_links || []);
  };

  if (loading) {
    return (
      <div className="main-content">
        <div className="loading"><div className="spinner" /></div>
      </div>
    );
  }

  return (
    <div className="main-content">
      {/* Hero */}
      {featuredContent && (
        <section className="hero">
          <div
            className="hero-bg"
            style={{ backgroundImage: `url(${getBackdropUrl(featuredContent.backdrop_path)})` }}
          />
          <div className="hero-content">
            <h1>{featuredContent.title || featuredContent.name}</h1>
            <p>{featuredContent.overview}</p>
            <div className="cta-buttons">
              <button className="btn-primary" onClick={handleFeaturedWatch}>
                <Play size={18} /> Watch Now
              </button>
              <button
                className="btn-secondary"
                onClick={() =>
                  navigate(`/${featuredContent.title ? 'movie' : 'tv'}/${featuredContent.id}`)
                }
              >
                <TrendingUp size={18} /> More Info
              </button>
            </div>
          </div>
        </section>
      )}

      {/* Trending Movies */}
      {!!trendingMovies.length && (
        <div className="content-section">
          <div className="section-header">
            <h2 className="section-title">🔥 Trending Movies</h2>
            <Link to="/movies" className="see-all">See All</Link>
          </div>
          <div className="content-grid">
            {trendingMovies.slice(0, 8).map(movie => (
              <ContentCard
                key={movie.id}
                item={movie}
                type="movie"
                onClick={handleContentClick}
                onWatch={handleWatchClick}
                onDownload={handleDownloadClick}
              />
            ))}
          </div>
        </div>
      )}

      {/* Trending TV */}
      {!!trendingTVShows.length && (
        <div className="content-section">
          <div className="section-header">
            <h2 className="section-title">📺 Trending TV Shows</h2>
            <Link to="/tv-shows" className="see-all">See All</Link>
          </div>
          <div className="content-grid">
            {trendingTVShows.slice(0, 8).map(show => (
              <ContentCard
                key={show.id}
                item={show}
                type="tv"
                onClick={handleContentClick}
                onWatch={handleWatchClick}
                onDownload={handleDownloadClick}
              />
            ))}
          </div>
        </div>
      )}

      {/* Popular Movies */}
      {!!popularMovies.length && (
        <div className="content-section">
          <div className="section-header">
            <h2 className="section-title">🎬 Popular Movies</h2>
            <Link to="/movies" className="see-all">See All</Link>
          </div>
          <div className="content-grid">
            {popularMovies.slice(0, 8).map(movie => (
              <ContentCard
                key={movie.id}
                item={movie}
                type="movie"
                onClick={handleContentClick}
                onWatch={handleWatchClick}
                onDownload={handleDownloadClick}
              />
            ))}
          </div>
        </div>
      )}

      {/* Popular TV */}
      {!!popularTVShows.length && (
        <div className="content-section">
          <div className="section-header">
            <h2 className="section-title">⭐ Popular TV Shows</h2>
            <Link to="/tv-shows" className="see-all">See All</Link>
          </div>
          <div className="content-grid">
            {popularTVShows.slice(0, 8).map(show => (
              <ContentCard
                key={show.id}
                item={show}
                type="tv"
                onClick={handleContentClick}
                onWatch={handleWatchClick}
                onDownload={handleDownloadClick}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
