import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, Play, Tv, Bookmark, Menu } from 'lucide-react';

const items = [
  { path: '/', icon: Home, label: 'Home' },
  { path: '/movies', icon: Play, label: 'Movies' },
  { path: '/tv-shows', icon: Tv, label: 'TV Series' },
  { path: '/watchlist', icon: Bookmark, label: 'Watch List' },
  { path: '/more', icon: Menu, label: 'More' }, // route optional
];

export default function BottomNavigation() {
  const { pathname } = useLocation();
  return (
    <nav className="bottom-navigation">
      {items.map(({ path, icon: Icon, label }) => (
        <Link key={path} to={path} className={`nav-item ${pathname === path ? 'active' : ''}`}>
          <Icon size={22} />
          <span className="nav-label">{label}</span>
        </Link>
      ))}
    </nav>
  );
}
