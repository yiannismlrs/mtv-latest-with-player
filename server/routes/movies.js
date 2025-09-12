const express = require('express');
const router = express.Router();
const db = require('../config/database');
const apiIntegration = require('../utils/apiIntegration');

// Get popular movies
router.get('/popular', async (req, res) => {
  try {
    const page = req.query.page || 1;
    
    // Get popular movies from TMDB
    const tmdbData = await apiIntegration.getPopularMoviesTMDB(page);
    
    if (tmdbData && tmdbData.results) {
      // Process and store movies with streaming/download links
      const moviesWithLinks = await Promise.all(
        tmdbData.results.map(async (movie) => {
          const releaseYear = movie.release_date ? new Date(movie.release_date).getFullYear() : null;
          
          // Get streaming and download links
          const streamingLinks = await apiIntegration.getStreamingLinks('movie', movie.id, movie.title, releaseYear);
          const downloadLinks = await apiIntegration.getDownloadLinks('movie', movie.id, movie.title, releaseYear);
          
          return {
            ...movie,
            streaming_links: streamingLinks,
            download_links: downloadLinks
          };
        })
      );
      
      res.json({
        success: true,
        results: moviesWithLinks,
        page: parseInt(page),
        total_pages: tmdbData.total_pages
      });
    } else {
      res.status(500).json({ success: false, error: 'Failed to fetch movies' });
    }
  } catch (error) {
    console.error('Popular movies error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// Search movies
router.get('/search', async (req, res) => {
  try {
    const { query, page = 1 } = req.query;
    
    if (!query) {
      return res.status(400).json({ success: false, error: 'Search query is required' });
    }
    
    const tmdbData = await apiIntegration.searchMoviesTMDB(query, page);
    
    if (tmdbData && tmdbData.results) {
      const moviesWithLinks = await Promise.all(
        tmdbData.results.map(async (movie) => {
          const releaseYear = movie.release_date ? new Date(movie.release_date).getFullYear() : null;
          
          // Get streaming and download links
          const streamingLinks = await apiIntegration.getStreamingLinks('movie', movie.id, movie.title, releaseYear);
          const downloadLinks = await apiIntegration.getDownloadLinks('movie', movie.id, movie.title, releaseYear);
          
          return {
            ...movie,
            streaming_links: streamingLinks,
            download_links: downloadLinks
          };
        })
      );
      
      res.json({
        success: true,
        results: moviesWithLinks,
        page: parseInt(page),
        total_pages: tmdbData.total_pages
      });
    } else {
      res.status(500).json({ success: false, error: 'Failed to search movies' });
    }
  } catch (error) {
    console.error('Movie search error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// Get movie details
router.get('/:id', async (req, res) => {
  try {
    const movieId = req.params.id;
    
    // Get movie details from TMDB
    const movieDetails = await apiIntegration.getMovieDetailsTMDB(movieId);
    
    if (movieDetails) {
      const releaseYear = movieDetails.release_date ? new Date(movieDetails.release_date).getFullYear() : null;
      
      // Get streaming and download links
      const streamingLinks = await apiIntegration.getStreamingLinks('movie', movieId, movieDetails.title, releaseYear);
      const downloadLinks = await apiIntegration.getDownloadLinks('movie', movieId, movieDetails.title, releaseYear);
      
      // Store in database
      const genres = JSON.stringify(movieDetails.genres || []);
      const streamUrls = JSON.stringify(streamingLinks || []);
      const downloadUrls = JSON.stringify(downloadLinks || []);
      
      db.run(`
        INSERT OR REPLACE INTO movies 
        (tmdb_id, title, overview, release_date, poster_path, backdrop_path, vote_average, vote_count, runtime, genres, stream_urls, download_urls)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `, [
        movieDetails.id,
        movieDetails.title,
        movieDetails.overview,
        movieDetails.release_date,
        movieDetails.poster_path,
        movieDetails.backdrop_path,
        movieDetails.vote_average,
        movieDetails.vote_count,
        movieDetails.runtime,
        genres,
        streamUrls,
        downloadUrls
      ]);
      
      res.json({
        success: true,
        movie: {
          ...movieDetails,
          streaming_links: streamingLinks,
          download_links: downloadLinks
        }
      });
    } else {
      res.status(404).json({ success: false, error: 'Movie not found' });
    }
  } catch (error) {
    console.error('Movie details error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// Get trending movies
router.get('/trending/day', async (req, res) => {
  try {
    // Get trending movies from TMDB
    const page = req.query.page || 1;
    const tmdbData = await apiIntegration.getTrendingMoviesTMDB(page);
    
    if (tmdbData && tmdbData.results) {
      const moviesWithLinks = await Promise.all(
        tmdbData.results.slice(0, 10).map(async (movie) => {
          const releaseYear = movie.release_date ? new Date(movie.release_date).getFullYear() : null;
          
          const streamingLinks = await apiIntegration.getStreamingLinks('movie', movie.id, movie.title, releaseYear);
          const downloadLinks = await apiIntegration.getDownloadLinks('movie', movie.id, movie.title, releaseYear);
          
          return {
            ...movie,
            streaming_links: streamingLinks,
            download_links: downloadLinks
          };
        })
      );
      
      res.json({
        success: true,
        results: moviesWithLinks
      });
    } else {
      res.status(500).json({ success: false, error: 'Failed to fetch trending movies' });
    }
  } catch (error) {
    console.error('Trending movies error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;
