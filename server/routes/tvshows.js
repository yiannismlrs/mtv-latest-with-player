const express = require('express');
const router = express.Router();
const db = require('../config/database');
const apiIntegration = require('../utils/apiIntegration');

// Get popular TV shows
router.get('/popular', async (req, res) => {
  try {
    const page = req.query.page || 1;
    
    const tmdbData = await apiIntegration.getPopularTVShowsTMDB(page);
    
    if (tmdbData && tmdbData.results) {
      const showsWithLinks = await Promise.all(
        tmdbData.results.map(async (show) => {
          const releaseYear = show.first_air_date ? new Date(show.first_air_date).getFullYear() : null;
          
          // Get streaming and download links
          const streamingLinks = await apiIntegration.getStreamingLinks('tv', show.id, show.name, releaseYear);
          const downloadLinks = await apiIntegration.getDownloadLinks('tv', show.id, show.name, releaseYear);
          
          return {
            ...show,
            streaming_links: streamingLinks,
            download_links: downloadLinks
          };
        })
      );
      
      res.json({
        success: true,
        results: showsWithLinks,
        page: parseInt(page),
        total_pages: tmdbData.total_pages
      });
    } else {
      res.status(500).json({ success: false, error: 'Failed to fetch TV shows' });
    }
  } catch (error) {
    console.error('Popular TV shows error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// Search TV shows
router.get('/search', async (req, res) => {
  try {
    const { query, page = 1 } = req.query;
    
    if (!query) {
      return res.status(400).json({ success: false, error: 'Search query is required' });
    }
    
    const tmdbData = await apiIntegration.searchTVShowsTMDB(query, page);
    
    if (tmdbData && tmdbData.results) {
      const showsWithLinks = await Promise.all(
        tmdbData.results.map(async (show) => {
          const releaseYear = show.first_air_date ? new Date(show.first_air_date).getFullYear() : null;
          
          // Get streaming and download links
          const streamingLinks = await apiIntegration.getStreamingLinks('tv', show.id, show.name, releaseYear);
          const downloadLinks = await apiIntegration.getDownloadLinks('tv', show.id, show.name, releaseYear);
          
          return {
            ...show,
            streaming_links: streamingLinks,
            download_links: downloadLinks
          };
        })
      );
      
      res.json({
        success: true,
        results: showsWithLinks,
        page: parseInt(page),
        total_pages: tmdbData.total_pages
      });
    } else {
      res.status(500).json({ success: false, error: 'Failed to search TV shows' });
    }
  } catch (error) {
    console.error('TV show search error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// Get TV show details
router.get('/:id', async (req, res) => {
  try {
    const showId = req.params.id;
    
    // Get TV show details from TMDB
    const showDetails = await apiIntegration.getTVShowDetailsTMDB(showId);
    
    if (showDetails) {
      const releaseYear = showDetails.first_air_date ? new Date(showDetails.first_air_date).getFullYear() : null;
      
      // Get streaming and download links
      const streamingLinks = await apiIntegration.getStreamingLinks('tv', showId, showDetails.name, releaseYear);
      const downloadLinks = await apiIntegration.getDownloadLinks('tv', showId, showDetails.name, releaseYear);
      
      // Store in database
      const genres = JSON.stringify(showDetails.genres || []);
      const streamUrls = JSON.stringify(streamingLinks || []);
      const downloadUrls = JSON.stringify(downloadLinks || []);
      
      db.run(`
        INSERT OR REPLACE INTO tv_shows 
        (tmdb_id, name, overview, first_air_date, poster_path, backdrop_path, vote_average, vote_count, 
         number_of_seasons, number_of_episodes, genres, stream_urls, download_urls)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `, [
        showDetails.id,
        showDetails.name,
        showDetails.overview,
        showDetails.first_air_date,
        showDetails.poster_path,
        showDetails.backdrop_path,
        showDetails.vote_average,
        showDetails.vote_count,
        showDetails.number_of_seasons,
        showDetails.number_of_episodes,
        genres,
        streamUrls,
        downloadUrls
      ]);
      
      res.json({
        success: true,
        tv_show: {
          ...showDetails,
          streaming_links: streamingLinks,
          download_links: downloadLinks
        }
      });
    } else {
      res.status(404).json({ success: false, error: 'TV show not found' });
    }
  } catch (error) {
    console.error('TV show details error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// Get TV show seasons and episodes
router.get('/:id/season/:season_number', async (req, res) => {
  try {
    const { id, season_number } = req.params;
    
    // This would typically use TMDB season endpoint
    // For now, we'll return a placeholder structure
    const seasonData = {
      success: true,
      season_number: parseInt(season_number),
      episodes: [
        // This would be populated with actual episode data from TMDB
        {
          id: 1,
          name: `Episode 1`,
          overview: 'Episode description...',
          episode_number: 1,
          air_date: '2023-01-01',
          runtime: 45,
          streaming_links: [],
          download_links: []
        }
      ]
    };
    
    res.json(seasonData);
  } catch (error) {
    console.error('TV show season error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// Get trending TV shows
router.get('/trending/day', async (req, res) => {
  try {
    const page = req.query.page || 1;
    const tmdbData = await apiIntegration.getTrendingTVShowsTMDB(page);
    
    if (tmdbData && tmdbData.results) {
      const showsWithLinks = await Promise.all(
        tmdbData.results.slice(0, 10).map(async (show) => {
          const releaseYear = show.first_air_date ? new Date(show.first_air_date).getFullYear() : null;
          
          const streamingLinks = await apiIntegration.getStreamingLinks('tv', show.id, show.name, releaseYear);
          const downloadLinks = await apiIntegration.getDownloadLinks('tv', show.id, show.name, releaseYear);
          
          return {
            ...show,
            streaming_links: streamingLinks,
            download_links: downloadLinks
          };
        })
      );
      
      res.json({
        success: true,
        results: showsWithLinks
      });
    } else {
      res.status(500).json({ success: false, error: 'Failed to fetch trending TV shows' });
    }
  } catch (error) {
    console.error('Trending TV shows error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;
