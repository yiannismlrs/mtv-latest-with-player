package com.mtv.streaming.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import java.util.concurrent.CompletableFuture;

/**
 * Alternative download approach using external torrent/download apps
 * For when direct streaming downloads are not possible
 */
public class TorrentDownloadManager {
    private static final String TAG = "TorrentDownloadManager";
    private final Context context;
    
    public TorrentDownloadManager(Context context) {
        this.context = context;
    }
    
    /**
     * Search for torrent/download links for the given movie/TV show
     */
    public CompletableFuture<Boolean> searchForDownloads(String title, String year, String type, String season, String episode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Searching for downloads: " + title + " (" + year + ")");
                
                // Build search query
                String searchQuery = buildSearchQuery(title, year, type, season, episode);
                Log.d(TAG, "Search query: " + searchQuery);
                
                // Try different download/torrent apps
                boolean success = tryTorrentApps(searchQuery) || 
                                tryDownloadApps(searchQuery) || 
                                tryBrowserSearch(searchQuery);
                
                return success;
                
            } catch (Exception e) {
                Log.e(TAG, "Search failed: " + e.getMessage());
                return false;
            }
        });
    }
    
    private String buildSearchQuery(String title, String year, String type, String season, String episode) {
        StringBuilder query = new StringBuilder();
        query.append(title);
        
        if (year != null && !year.isEmpty()) {
            query.append(" ").append(year);
        }
        
        if ("tv".equals(type) && season != null && episode != null) {
            query.append(" S").append(String.format("%02d", Integer.parseInt(season)));
            query.append("E").append(String.format("%02d", Integer.parseInt(episode)));
        }
        
        // Add quality indicators for better results
        query.append(" 1080p");
        
        return query.toString();
    }
    
    private boolean tryTorrentApps(String searchQuery) {
        try {
            // Try popular torrent apps
            String[] torrentApps = {
                "com.utorrent.client",           // uTorrent
                "com.bittorrent.client",         // BitTorrent
                "com.transdroid.search",         // Transdroid
                "com.vuze.torrent.downloader"    // Vuze
            };
            
            for (String packageName : torrentApps) {
                if (isAppInstalled(packageName)) {
                    Log.d(TAG, "Found torrent app: " + packageName);
                    
                    Intent intent = new Intent(Intent.ACTION_SEARCH);
                    intent.putExtra("query", searchQuery);
                    intent.setPackage(packageName);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    try {
                        context.startActivity(intent);
                        showToast("Opening torrent app to search for: " + searchQuery);
                        return true;
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to open " + packageName + ": " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Torrent app search failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private boolean tryDownloadApps(String searchQuery) {
        try {
            // Try download manager apps
            String[] downloadApps = {
                "com.dv.adm",                    // Advanced Download Manager
                "idm.internet.download.manager", // IDM+
                "com.download.manager.plus"      // Download Manager Plus
            };
            
            for (String packageName : downloadApps) {
                if (isAppInstalled(packageName)) {
                    Log.d(TAG, "Found download app: " + packageName);
                    
                    Intent intent = new Intent(Intent.ACTION_SEARCH);
                    intent.putExtra("query", searchQuery);
                    intent.setPackage(packageName);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    try {
                        context.startActivity(intent);
                        showToast("Opening download app to search for: " + searchQuery);
                        return true;
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to open " + packageName + ": " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Download app search failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private boolean tryBrowserSearch(String searchQuery) {
        try {
            // Search on popular torrent/download sites
            String[] searchUrls = {
                "https://1337x.to/search/" + Uri.encode(searchQuery) + "/1/",
                "https://thepiratebay.org/search.php?q=" + Uri.encode(searchQuery),
                "https://yts.mx/browse-movies/" + Uri.encode(searchQuery),
                "https://www.google.com/search?q=" + Uri.encode(searchQuery + " download torrent")
            };
            
            // Open first available search URL in browser
            for (String searchUrl : searchUrls) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browserIntent);
                    
                    showToast("Opening browser to search for downloads");
                    Log.d(TAG, "Opened browser search: " + searchUrl);
                    return true;
                    
                } catch (Exception e) {
                    Log.w(TAG, "Failed to open browser search: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Browser search failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void showToast(String message) {
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
    }
}