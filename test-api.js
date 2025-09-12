const axios = require('axios');
require('dotenv').config({ path: './server/.env' });

const TMDB_API_KEY = process.env.TMDB_API_KEY;
const TMDB_BASE_URL = 'https://api.themoviedb.org/3';

async function testTMDBConnection() {
  try {
    console.log('Testing TMDB API connectivity...');
    console.log('API Key:', TMDB_API_KEY ? 'Present' : 'Missing');
    
    if (!TMDB_API_KEY) {
      console.error('TMDB_API_KEY is missing from environment variables');
      return;
    }
    
    // Test search for "game of thrones"
    const searchUrl = `${TMDB_BASE_URL}/search/tv?api_key=${TMDB_API_KEY}&query=game of thrones`;
    console.log('Making request to:', searchUrl);
    
    const response = await axios.get(searchUrl);
    console.log('TMDB Response Status:', response.status);
    console.log('TMDB Results:', response.data.results ? `${response.data.results.length} results found` : 'No results');
    
    if (response.data.results && response.data.results.length > 0) {
      console.log('First result:', response.data.results[0].name || response.data.results[0].title);
    }
    
  } catch (error) {
    console.error('TMDB API Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
  }
}

testTMDBConnection();
