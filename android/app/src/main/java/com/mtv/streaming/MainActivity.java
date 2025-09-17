package com.mtv.streaming;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.util.List;
import com.mtv.streaming.download.StreamDownloadManager;
import com.mtv.streaming.download.StreamDownloadManager.DownloadInfo;
import com.mtv.streaming.download.StreamDownloadManager.DownloadListener;
import com.mtv.streaming.download.TorrentDownloadManager;

public class MainActivity extends Activity implements StreamDownloadManager.DownloadListener {
    private WebView webView;
    private StreamDownloadManager downloadManager;
    private TorrentDownloadManager torrentManager;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize WebView
        webView = findViewById(R.id.webview);
        
        // Set WebView background to dark color to eliminate white box
        webView.setBackgroundColor(Color.parseColor("#0D1117"));
        webView.setBackgroundResource(0); // Remove any default background
        
        // Configure WebView for better performance and video playback
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setMediaPlaybackRequiresUserGesture(false); // Allow auto-play
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        
        // Set user agent for better compatibility
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " MTVApp/1.0");
        
        // Add JavaScript interface for native Android functions
        webView.addJavascriptInterface(new AndroidInterface(), "Android");
        
        // Set WebViewClient to handle URL loading
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Handle SPlayer intent URLs
                if (url.startsWith("intent://") && url.contains("splayer")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        // If SPlayer is not installed, open SPlayer website in external browser
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://splayer.dev/"));
                        startActivity(browserIntent);
                        return true;
                    }
                }
                // Handle SPlayer custom scheme URLs
                else if (url.startsWith("splayer://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        // If SPlayer is not installed, open website in external browser
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://splayer.dev/"));
                        startActivity(browserIntent);
                        return true;
                    }
                }
                // Handle external website URLs
                else if (url.startsWith("http://") || url.startsWith("https://")) {
                    // Allow VidLink URLs to load in WebView
                    if (url.contains("vidlink.pro")) {
                        return false; // Allow loading in WebView
                    }
                    // Check if it's not our local file - open other external URLs in browser
                    else if (!url.contains("file:///android_asset/")) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(browserIntent);
                        return true; // Prevent loading in WebView
                    }
                }
                // Allow local files to load in WebView
                return false;
            }
        });
        
        // Initialize download manager
        downloadManager = StreamDownloadManager.getInstance(this);
        downloadManager.addDownloadListener(this);
        
        // Initialize torrent/alternative download manager
        torrentManager = new TorrentDownloadManager(this);
        
        // Load the local web app
        webView.loadUrl("file:///android_asset/www/index.html");
    }
    
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    // JavaScript Interface Class
    public class AndroidInterface {
        @JavascriptInterface
        public boolean isSPlayerInstalled() {
            boolean installed = SPlayerUtil.isSPlayerInstalled(MainActivity.this);
            android.util.Log.d("MTV_DEBUG", "SPlayer installed check: " + installed);
            return installed;
        }
        
        @JavascriptInterface
        public void openInSPlayer(String mediaId, String mediaType, String title, String year) {
            openInVidLink(mediaId, mediaType, title, year, null, null);
        }
        
        @JavascriptInterface
        public void openInVidLink(String mediaId, String mediaType, String title, String year, String season, String episode) {
            android.util.Log.d("MTV_DEBUG", "=== openInVidLink called ===");
            android.util.Log.d("MTV_DEBUG", "Media ID: " + mediaId);
            android.util.Log.d("MTV_DEBUG", "Media Type: " + mediaType);
            android.util.Log.d("MTV_DEBUG", "Title: " + title);
            android.util.Log.d("MTV_DEBUG", "Year: " + year);
            
            runOnUiThread(() -> {
                try {
                    String vidlinkUrl = buildVidLinkUrl(mediaId, mediaType, season, episode);
                    if (vidlinkUrl != null) {
                        android.util.Log.d("MTV_DEBUG", "Opening VidLink URL: " + vidlinkUrl);
                        showToast("Opening VidLink player...");
                        
                        // Load VidLink URL directly in the current WebView
                        webView.loadUrl(vidlinkUrl);
                        
                        android.util.Log.d("MTV_DEBUG", "✓ Opened VidLink player successfully");
                    } else {
                        android.util.Log.e("MTV_DEBUG", "✗ Failed to build VidLink URL");
                        showToast("Unable to create VidLink URL for this content");
                    }
                } catch (Exception e) {
                    android.util.Log.e("MTV_DEBUG", "Failed to open VidLink: " + e.getMessage());
                    showToast("Failed to open VidLink player: " + e.getMessage());
                }
            });
        }
        
        /**
         * Build VidLink URL based on media type and TMDB ID
         */
        private String buildVidLinkUrl(String mediaId, String mediaType, String season, String episode) {
            if (mediaId == null || mediaId.trim().isEmpty()) {
                android.util.Log.e("MTV_DEBUG", "Missing TMDB ID");
                return null;
            }
            
            if ("movie".equals(mediaType)) {
                // Movie: https://vidlink.pro/movie/{tmdbId}
                return "https://vidlink.pro/movie/" + mediaId;
            } else if ("tv".equals(mediaType)) {
                // TV Show: https://vidlink.pro/tv/{tmdbId}/{season}/{episode}
                if (season != null && episode != null && !season.trim().isEmpty() && !episode.trim().isEmpty()) {
                    return "https://vidlink.pro/tv/" + mediaId + "/" + season + "/" + episode;
                } else {
                    // Return base TV URL - VidLink will handle episode selection
                    return "https://vidlink.pro/tv/" + mediaId;
                }
            } else {
                android.util.Log.e("MTV_DEBUG", "Unsupported media type: " + mediaType);
                return null;
            }
        }
        
        @JavascriptInterface
        public void downloadContent(String mediaId, String mediaType, String title, String year, String season, String episode) {
            android.util.Log.d("MTV_DEBUG", "=== downloadContent called ===");
            android.util.Log.d("MTV_DEBUG", "Media ID: " + mediaId);
            android.util.Log.d("MTV_DEBUG", "Media Type: " + mediaType);
            android.util.Log.d("MTV_DEBUG", "Title: " + title);
            android.util.Log.d("MTV_DEBUG", "Season: " + season + ", Episode: " + episode);
            
            runOnUiThread(() -> {
                try {
                    String embedUrl = buildVidLinkUrl(mediaId, mediaType, season, episode);
                    if (embedUrl != null) {
                        android.util.Log.d("MTV_DEBUG", "Starting download from embed: " + embedUrl);
                        showToast("Analyzing video source...");
                        
                        // Start download with embed URL
                        downloadManager.downloadVideo(embedUrl, title, mediaType, season, episode)
                            .thenAccept(downloadInfo -> {
                                runOnUiThread(() -> {
                                    showToast("Download started: " + downloadInfo.filename);
                                    android.util.Log.d("MTV_DEBUG", "✓ Download started successfully");
                                });
                            })
                            .exceptionally(throwable -> {
                                runOnUiThread(() -> {
                                    android.util.Log.e("MTV_DEBUG", "✗ Download failed: " + throwable.getMessage());
                                    showToast("Stream protected. Opening alternative download options...");
                                    // Try alternative download methods
                                    tryAlternativeDownload(title, year, mediaType, season, episode);
                                });
                                return null;
                            });
                    } else {
                        android.util.Log.e("MTV_DEBUG", "✗ Failed to build embed URL for download");
                        showToast("Unable to create embed URL for this content");
                    }
                } catch (Exception e) {
                    android.util.Log.e("MTV_DEBUG", "Failed to start download: " + e.getMessage());
                    showToast("Failed to start download: " + e.getMessage());
                }
            });
        }
        
        /**
         * Try alternative download methods when direct download fails
         */
        private void tryAlternativeDownload(String title, String year, String mediaType, String season, String episode) {
            android.util.Log.d("MTV_DEBUG", "Trying alternative download for: " + title);
            
            torrentManager.searchForDownloads(title, year, mediaType, season, episode)
                .thenAccept(success -> {
                    runOnUiThread(() -> {
                        if (success) {
                            showToast("Opened external app for download search");
                        } else {
                            showToast("No download apps found. Install a torrent client or download manager.");
                        }
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showToast("Alternative download search failed");
                    });
                    return null;
                });
        }
        
        @JavascriptInterface
        public String getActiveDownloads() {
            try {
                List<DownloadInfo> downloads = downloadManager.getActiveDownloads();
                // Convert to JSON string for JavaScript
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < downloads.size(); i++) {
                    DownloadInfo download = downloads.get(i);
                    if (i > 0) json.append(",");
                    json.append("{");
                    json.append("\"id\":" + download.downloadId + ",");
                    json.append("\"title\":\"" + download.title.replace("\"", "\\\"") + "\",");
                    json.append("\"filename\":\"" + download.filename.replace("\"", "\\\"") + "\",");
                    json.append("\"progress\":" + download.progress + ",");
                    json.append("\"status\":\"" + download.status + "\",");
                    json.append("\"type\":\"" + download.type + "\"");
                    if (download.season != null) {
                        json.append(",\"season\":\"" + download.season + "\"");
                        json.append(",\"episode\":\"" + download.episode + "\"");
                    }
                    json.append("}");
                }
                json.append("]");
                return json.toString();
            } catch (Exception e) {
                android.util.Log.e("MTV_DEBUG", "Failed to get active downloads: " + e.getMessage());
                return "[]";
            }
        }
        
        @JavascriptInterface
        public String getCompletedDownloads() {
            try {
                List<DownloadInfo> downloads = downloadManager.getCompletedDownloads();
                // Convert to JSON string for JavaScript
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < downloads.size(); i++) {
                    DownloadInfo download = downloads.get(i);
                    if (i > 0) json.append(",");
                    json.append("{");
                    json.append("\"title\":\"" + download.title.replace("\"", "\\\"") + "\",");
                    json.append("\"filename\":\"" + download.filename.replace("\"", "\\\"") + "\",");
                    json.append("\"filePath\":\"" + download.filePath.replace("\"", "\\\"") + "\",");
                    json.append("\"type\":\"" + download.type + "\"");
                    if (download.season != null) {
                        json.append(",\"season\":\"" + download.season + "\"");
                        json.append(",\"episode\":\"" + download.episode + "\"");
                    }
                    json.append("}");
                }
                json.append("]");
                return json.toString();
            } catch (Exception e) {
                android.util.Log.e("MTV_DEBUG", "Failed to get completed downloads: " + e.getMessage());
                return "[]";
            }
        }
        
        @JavascriptInterface
        public void cancelDownload(String downloadId) {
            try {
                long id = Long.parseLong(downloadId);
                downloadManager.cancelDownload(id);
                showToast("Download cancelled");
            } catch (Exception e) {
                android.util.Log.e("MTV_DEBUG", "Failed to cancel download: " + e.getMessage());
                showToast("Failed to cancel download");
            }
        }
        
        @JavascriptInterface
        public void playDownloadedFile(String filePath) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + filePath), "video/*");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("MTV_DEBUG", "Failed to play downloaded file: " + e.getMessage());
                showToast("Failed to open video file");
            }
        }
        
        @JavascriptInterface
        public void openExternalUrl(String url) {
            android.util.Log.d("MTV_DEBUG", "openExternalUrl called with: " + url);
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            } catch (Exception e) {
                android.util.Log.e("MTV_DEBUG", "Failed to open external URL: " + e.getMessage());
            }
        }
        
        private void showToast(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        }
    }
    
    // DownloadListener implementation
    @Override
    public void onDownloadStarted(DownloadInfo download) {
        runOnUiThread(() -> {
            Log.d("MTV_DEBUG", "Download started: " + download.title);
            // Notify frontend via JavaScript if needed
            if (webView != null) {
                webView.evaluateJavascript("if(window.app && window.app.onDownloadStarted) window.app.onDownloadStarted(" + download.downloadId + ");", null);
            }
        });
    }
    
    @Override
    public void onDownloadProgress(DownloadInfo download, int progress) {
        runOnUiThread(() -> {
            // Notify frontend via JavaScript
            if (webView != null) {
                webView.evaluateJavascript("if(window.app && window.app.onDownloadProgress) window.app.onDownloadProgress(" + download.downloadId + ", " + progress + ");", null);
            }
        });
    }
    
    @Override
    public void onDownloadCompleted(DownloadInfo download, String filePath) {
        runOnUiThread(() -> {
            Log.d("MTV_DEBUG", "Download completed: " + download.title + " at " + filePath);
            // Notify frontend via JavaScript
            if (webView != null) {
                webView.evaluateJavascript("if(window.app && window.app.onDownloadCompleted) window.app.onDownloadCompleted(" + download.downloadId + ", '" + filePath + "');", null);
            }
        });
    }
    
    @Override
    public void onDownloadFailed(DownloadInfo download, String error) {
        runOnUiThread(() -> {
            Log.e("MTV_DEBUG", "Download failed: " + download.title + " - " + error);
            // Notify frontend via JavaScript
            if (webView != null) {
                webView.evaluateJavascript("if(window.app && window.app.onDownloadFailed) window.app.onDownloadFailed(" + download.downloadId + ", '" + error + "');", null);
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadManager != null) {
            downloadManager.removeDownloadListener(this);
            downloadManager.cleanup();
        }
    }
}