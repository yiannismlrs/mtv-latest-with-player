import React from 'react';

const MTVLogo = ({ width = 120, height = 60, className = "" }) => {
  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 200 100"
      className={className}
      xmlns="http://www.w3.org/2000/svg"
    >
      {/* TV Frame */}
      <rect
        x="10"
        y="20"
        width="180"
        height="60"
        rx="15"
        ry="15"
        fill="none"
        stroke="url(#orangeGradient)"
        strokeWidth="4"
      />
      
      {/* TV Screen */}
      <rect
        x="20"
        y="30"
        width="160"
        height="40"
        rx="8"
        ry="8"
        fill="#0D1117"
        stroke="url(#orangeGradient)"
        strokeWidth="1"
      />
      
      {/* MTV Text */}
      <text
        x="100"
        y="55"
        textAnchor="middle"
        className="mtv-text"
        fill="url(#textGradient)"
        fontSize="24"
        fontWeight="bold"
        fontFamily="Arial, sans-serif"
      >
        MTV
      </text>
      
      {/* Movies & TV Text */}
      <text
        x="100"
        y="95"
        textAnchor="middle"
        className="subtitle-text"
        fill="#FF8C42"
        fontSize="10"
        fontWeight="500"
        fontFamily="Arial, sans-serif"
        letterSpacing="2px"
      >
        MOVIES &amp; TV
      </text>
      
      {/* TV Antennas */}
      <line
        x1="80"
        y1="20"
        x2="60"
        y2="5"
        stroke="url(#orangeGradient)"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <circle cx="60" cy="5" r="3" fill="#FF6B35" />
      
      <line
        x1="120"
        y1="20"
        x2="140"
        y2="5"
        stroke="url(#orangeGradient)"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <circle cx="140" cy="5" r="3" fill="#FF6B35" />
      
      {/* Gradients */}
      <defs>
        <linearGradient id="orangeGradient" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#FF6B35" />
          <stop offset="100%" stopColor="#FF8C42" />
        </linearGradient>
        
        <linearGradient id="textGradient" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#FFB885" />
          <stop offset="50%" stopColor="#FF8C42" />
          <stop offset="100%" stopColor="#FF6B35" />
        </linearGradient>
      </defs>
    </svg>
  );
};

export default MTVLogo;
