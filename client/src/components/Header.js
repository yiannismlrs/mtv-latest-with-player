import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';

const Header = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const navigate = useNavigate();
  const location = useLocation();

  // Hide header on detail & player screens
  const hideHeader =
    /^\/(movie|tv)\//.test(location.pathname) || location.pathname.startsWith('/watch');

  if (hideHeader) return null;

  const onSearch = (e) => {
    e.preventDefault();
    const q = searchQuery.trim();
    if (q) navigate(`/search?query=${encodeURIComponent(q)}`);
  };

  return (
    <header className="header site-header">
      <div className="header-content">
        <Link to="/" className="logo">
          <span className="logo-icon">MTV</span>
        </Link>

        <nav className="nav">
          <Link className={location.pathname === '/' ? 'active' : ''} to="/">Home</Link>
          <Link className={location.pathname === '/movies' ? 'active' : ''} to="/movies">Movies</Link>
          <Link className={location.pathname === '/tv-shows' ? 'active' : ''} to="/tv-shows">TV Series</Link>
          <Link className={location.pathname === '/watchlist' ? 'active' : ''} to="/watchlist">Watch List</Link>
        </nav>

        <form className="search-bar" onSubmit={onSearch}>
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search movies, TV shows..."
          />
          <button className="search-btn" type="submit">🔍</button>
        </form>
      </div>
    </header>
  );
};

export default Header;
