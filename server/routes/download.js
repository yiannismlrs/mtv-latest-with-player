const express = require('express');
const router = express.Router();
const apiIntegration = require('../utils/apiIntegration');

// Download links for movies/TV shows
router.get('/', async (req, res) => {
  try {
    const { type, id, title, year } = req.query; // type: 'movie' or 'tv'

    if (!type || !title) {
      return res.status(400).json({ success: false, error: 'type and title are required' });
    }

    const links = await apiIntegration.getDownloadLinks(type, id, title, year);
    res.json({ success: true, results: links });
  } catch (error) {
    console.error('Download links error:', error);
    res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;
