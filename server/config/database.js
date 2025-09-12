const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// Database file path
const DB_PATH = path.join(__dirname, '..', 'database', 'onstream.db');

// Create database connection
const db = new sqlite3.Database(DB_PATH, (err) => {
  if (err) {
    console.error('Error opening database:', err.message);
  } else {
    console.log('Connected to SQLite database');
    initializeDatabase();
  }
});

// Initialize database tables
function initializeDatabase() {
  // Movies table
  db.run(`
    CREATE TABLE IF NOT EXISTS movies (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      tmdb_id INTEGER UNIQUE,
      title TEXT NOT NULL,
      overview TEXT,
      release_date TEXT,
      poster_path TEXT,
      backdrop_path TEXT,
      vote_average REAL,
      vote_count INTEGER,
      runtime INTEGER,
      genres TEXT,
      stream_urls TEXT,
      download_urls TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
  `);

  // TV Shows table
  db.run(`
    CREATE TABLE IF NOT EXISTS tv_shows (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      tmdb_id INTEGER UNIQUE,
      name TEXT NOT NULL,
      overview TEXT,
      first_air_date TEXT,
      poster_path TEXT,
      backdrop_path TEXT,
      vote_average REAL,
      vote_count INTEGER,
      number_of_seasons INTEGER,
      number_of_episodes INTEGER,
      genres TEXT,
      stream_urls TEXT,
      download_urls TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
  `);

  // TV Show Episodes table
  db.run(`
    CREATE TABLE IF NOT EXISTS episodes (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      tv_show_id INTEGER,
      season_number INTEGER,
      episode_number INTEGER,
      name TEXT,
      overview TEXT,
      air_date TEXT,
      runtime INTEGER,
      still_path TEXT,
      stream_urls TEXT,
      download_urls TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (tv_show_id) REFERENCES tv_shows (id)
    )
  `);

  // User favorites table (for future use if needed)
  db.run(`
    CREATE TABLE IF NOT EXISTS favorites (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      content_type TEXT CHECK(content_type IN ('movie', 'tv_show')),
      content_id INTEGER,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
  `);

  console.log('Database tables initialized');
}

module.exports = db;
