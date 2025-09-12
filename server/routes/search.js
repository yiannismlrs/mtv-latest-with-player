const express = require('express');
const router = express.Router();
const apiIntegration = require('../utils/apiIntegration');

// Combined search for movies and TV shows
router.get('/', async (req, res) => {
  try {
    const { query, page = 1 } = req.query;

    if (!query) {
      return res.status(400).json({ success: false, error: 'Search query is required' });
    }

    const [movies, tv] = await Promise.all([
      apiIntegration.searchMoviesTMDB(query, page),
      apiIntegration.searchTVShowsTMDB(query, page)
    ]);

    res.json({ success: true, movies: movies?.results || [], tv: tv?.results || [] });
  } catch (error) {
    console.error('Search error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;
