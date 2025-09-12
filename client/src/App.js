import React from 'react';
import { BrowserRouter as Router, Routes, Route, useLocation } from 'react-router-dom';

import BottomNavigation from './components/BottomNavigation';
import Header from './components/Header'; // hidden on mobile via CSS

import HomePage from './pages/HomePage';
import SearchResults from './pages/SearchResults';
import MoviePage from './pages/MoviePage';
import TVShowPage from './pages/TVShowPage';
import WatchPage from './pages/WatchPage';

import './App.css';

function Shell() {
  const { pathname } = useLocation();
  const hideBottomNav =
    /^\/(movie|tv)\//.test(pathname) || pathname.startsWith('/watch');

  return (
    <>
      {/* For mobile-first you can keep Header minimal; hide via CSS below 768px */}
      <Header />

      <main className="main-content">
        <Routes>
          <Route path="/" element={<HomePage />} />

          {/* Lists */}
          <Route path="/movies" element={<SearchResults type="movie" />} />
          <Route path="/tv-shows" element={<SearchResults type="tv" />} />
          <Route path="/watchlist" element={<SearchResults type="watchlist" />} />
          <Route path="/search" element={<SearchResults />} />

          {/* Details */}
          <Route path="/movie/:id" element={<MoviePage />} />
          <Route path="/tv/:id" element={<TVShowPage />} />

          {/* Player */}
          <Route path="/watch" element={<WatchPage />} />
        </Routes>
      </main>

      {!hideBottomNav && <BottomNavigation />}
    </>
  );
}

export default function App() {
  return (
    <Router>
      <Shell />
    </Router>
  );
}
