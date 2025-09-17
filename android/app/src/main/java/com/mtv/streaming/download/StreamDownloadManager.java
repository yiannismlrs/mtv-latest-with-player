package com.mtv.streaming.download;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Advanced download manager that can extract and download from embed URLs
 * Uses multiple extraction methods for maximum compatibility
 */
public class StreamDownloadManager {
    private static final String TAG = "StreamDownloadManager";
    private static StreamDownloadManager instance;
    private final Context context;
    private final DownloadManager downloadManager;
    private final Map<Long, DownloadInfo> activeDownloads;
    private final List<DownloadListener> listeners;
    private final Handler mainHandler;
    
    public interface DownloadListener {
        void onDownloadStarted(DownloadInfo download);
        void onDownloadProgress(DownloadInfo download, int progress);
        void onDownloadCompleted(DownloadInfo download, String filePath);
        void onDownloadFailed(DownloadInfo download, String error);
    }
    
    public static class DownloadInfo {
        public final long downloadId;
        public final String title;
        public final String originalUrl;
        public final String actualUrl;
        public final String filename;
        public final String season;
        public final String episode;
        public final String type;
        public int progress;
        public String status;
        public String filePath;
        
        public DownloadInfo(long downloadId, String title, String originalUrl, String actualUrl, 
                          String filename, String type, String season, String episode) {
            this.downloadId = downloadId;
            this.title = title;
            this.originalUrl = originalUrl;
            this.actualUrl = actualUrl;
            this.filename = filename;
            this.type = type;
            this.season = season;
            this.episode = episode;
            this.progress = 0;
            this.status = "Starting";
        }
    }
    
    private StreamDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        this.activeDownloads = new HashMap<>();
        this.listeners = new ArrayList<>();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Register download completion receiver
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        context.registerReceiver(downloadReceiver, filter);
    }
    
    public static synchronized StreamDownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new StreamDownloadManager(context);
        }
        return instance;
    }
    
    /**
     * Download video from embed URL using multiple extraction methods
     */
    public CompletableFuture<DownloadInfo> downloadVideo(String embedUrl, String title, String type, String season, String episode) {
        Log.d(TAG, "=== Starting Advanced Download ===");
        Log.d(TAG, "Title: " + title);
        Log.d(TAG, "Embed URL: " + embedUrl);
        Log.d(TAG, "Type: " + type + (season != null ? " S" + season + "E" + episode : ""));
        
        return extractStreamUrl(embedUrl, title)
            .thenCompose(streamUrl -> {
                if (streamUrl != null && !streamUrl.equals(embedUrl)) {
                    Log.d(TAG, "✓ Extracted direct stream URL: " + streamUrl);
                    return startDirectDownload(streamUrl, title, type, season, episode, embedUrl);
                } else {
                    Log.w(TAG, "Could not extract direct URL, trying alternative methods...");
                    return tryAlternativeDownloadMethods(embedUrl, title, type, season, episode);
                }
            });
    }
    
    /**
     * Extract actual stream URL from embed page using WebView interception
     */
    private CompletableFuture<String> extractStreamUrl(String embedUrl, String title) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        mainHandler.post(() -> {
            try {
                Log.d(TAG, "Creating WebView for stream extraction...");
                
                WebView webView = new WebView(context);
                AtomicBoolean streamFound = new AtomicBoolean(false);
                
                // Configure WebView for maximum compatibility
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setLoadWithOverviewMode(true);
                webView.getSettings().setUseWideViewPort(true);
                webView.getSettings().setAllowFileAccess(true);
                webView.getSettings().setAllowContentAccess(true);
                webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
                
                // Set realistic user agent
                webView.getSettings().setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                );
                
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        
                        // Intercept video stream requests
                        if (!streamFound.get() && isVideoStreamUrl(url)) {
                            Log.d(TAG, "🎯 Intercepted video stream: " + url);
                            streamFound.set(true);
                            
                            mainHandler.post(() -> {
                                future.complete(url);
                                webView.destroy();
                            });
                            
                            return null;
                        }
                        
                        // Log interesting requests for debugging
                        if (url.contains("stream") || url.contains("video") || url.contains("play")) {
                            Log.d(TAG, "📡 Interesting request: " + url);
                        }
                        
                        return super.shouldInterceptRequest(view, request);
                    }
                    
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        Log.d(TAG, "📄 Page loaded: " + url);
                        
                        // Wait for dynamic content, then try JavaScript extraction
                        mainHandler.postDelayed(() -> {
                            if (!streamFound.get()) {
                                extractWithJavaScript(view, future, streamFound, embedUrl);
                            }
                        }, 5000); // Wait 5 seconds for content to load
                    }
                    
                    @Override
                    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                        Log.w(TAG, "WebView error: " + description + " for " + failingUrl);
                        
                        if (!streamFound.get() && failingUrl.equals(embedUrl)) {
                            // Main page failed to load
                            future.complete(null);
                            webView.destroy();
                        }
                    }
                });
                
                // Load the embed page
                Log.d(TAG, "🌐 Loading embed page: " + embedUrl);
                webView.loadUrl(embedUrl);
                
                // Set timeout for extraction
                mainHandler.postDelayed(() -> {
                    if (!future.isDone()) {
                        Log.w(TAG, "⏰ Stream extraction timeout");
                        future.complete(null);
                        webView.destroy();
                    }
                }, 20000); // 20 second timeout
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error setting up WebView: " + e.getMessage());
                future.complete(null);
            }
        });
        
        return future;
    }
    
    /**
     * Check if URL is a video stream
     */
    private boolean isVideoStreamUrl(String url) {
        if (url == null) return false;
        
        String lower = url.toLowerCase();
        
        // Direct video files
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || 
            lower.endsWith(".webm") || lower.endsWith(".mov")) {
            return true;
        }
        
        // Streaming formats
        if (lower.endsWith(".m3u8") || lower.contains(".m3u8?") || 
            lower.endsWith(".mpd") || lower.contains(".mpd?")) {
            return true;
        }
        
        // Stream URLs with video indicators
        if ((lower.contains("stream") || lower.contains("video") || lower.contains("play")) &&
            (lower.contains("mp4") || lower.contains("m3u8") || lower.contains("hls"))) {
            return true;
        }
        
        // CDN patterns
        if (lower.matches(".*\\.(mp4|m3u8|mkv|avi|webm)\\?.*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Extract stream using JavaScript injection
     */
    private void extractWithJavaScript(WebView webView, CompletableFuture<String> future, 
                                     AtomicBoolean streamFound, String embedUrl) {
        
        Log.d(TAG, "🔍 Trying JavaScript extraction...");
        
        // Comprehensive JavaScript to find video sources
        String javascript = 
            "(function() {" +
            "  console.log('MTV: Starting JavaScript extraction');" +
            "  " +
            "  // Method 1: Check video elements" +
            "  var videos = document.querySelectorAll('video');" +
            "  for (var i = 0; i < videos.length; i++) {" +
            "    if (videos[i].src && videos[i].src.length > 10) {" +
            "      console.log('MTV: Found video src:', videos[i].src);" +
            "      return videos[i].src;" +
            "    }" +
            "    if (videos[i].currentSrc && videos[i].currentSrc.length > 10) {" +
            "      console.log('MTV: Found video currentSrc:', videos[i].currentSrc);" +
            "      return videos[i].currentSrc;" +
            "    }" +
            "  }" +
            "  " +
            "  // Method 2: Check source elements" +
            "  var sources = document.querySelectorAll('source');" +
            "  for (var i = 0; i < sources.length; i++) {" +
            "    if (sources[i].src && sources[i].src.length > 10) {" +
            "      console.log('MTV: Found source src:', sources[i].src);" +
            "      return sources[i].src;" +
            "    }" +
            "  }" +
            "  " +
            "  // Method 3: Check for HLS.js or similar players" +
            "  if (window.hls && window.hls.url) {" +
            "    console.log('MTV: Found HLS.js URL:', window.hls.url);" +
            "    return window.hls.url;" +
            "  }" +
            "  " +
            "  // Method 4: Check global variables" +
            "  var globals = ['videoUrl', 'streamUrl', 'playerUrl', 'source', 'src'];" +
            "  for (var i = 0; i < globals.length; i++) {" +
            "    if (window[globals[i]] && typeof window[globals[i]] === 'string' && window[globals[i]].length > 10) {" +
            "      console.log('MTV: Found global var', globals[i], ':', window[globals[i]]);" +
            "      return window[globals[i]];" +
            "    }" +
            "  }" +
            "  " +
            "  // Method 5: Check iframe sources" +
            "  var iframes = document.querySelectorAll('iframe');" +
            "  for (var i = 0; i < iframes.length; i++) {" +
            "    var src = iframes[i].src;" +
            "    if (src && (src.includes('stream') || src.includes('play') || src.includes('video'))) {" +
            "      console.log('MTV: Found iframe with video src:', src);" +
            "      return src;" +
            "    }" +
            "  }" +
            "  " +
            "  console.log('MTV: No video sources found');" +
            "  return null;" +
            "})()";
        
        webView.evaluateJavascript(javascript, result -> {
            if (!streamFound.get()) {
                if (result != null && !result.equals("null") && !result.equals("\"\"")) {
                    String cleanUrl = result.replace("\"", "");
                    if (cleanUrl.startsWith("http") && isVideoStreamUrl(cleanUrl)) {
                        Log.d(TAG, "✓ JavaScript found stream: " + cleanUrl);
                        streamFound.set(true);
                        future.complete(cleanUrl);
                        webView.destroy();
                        return;
                    }
                }
                
                Log.d(TAG, "❌ JavaScript extraction failed");
                future.complete(null);
                webView.destroy();
            }
        });
    }
    
    /**
     * Try alternative download methods when direct extraction fails
     */
    private CompletableFuture<DownloadInfo> tryAlternativeDownloadMethods(String embedUrl, String title, String type, String season, String episode) {
        Log.d(TAG, "🔄 Trying alternative download methods...");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Method 1: Try known working stream URLs based on TMDB ID
                String tmdbId = extractTmdbId(embedUrl);
                if (tmdbId != null) {
                    Log.d(TAG, "📋 Extracted TMDB ID: " + tmdbId);
                    
                    String[] alternativeUrls = buildAlternativeStreamUrls(tmdbId, type, season, episode);
                    
                    for (String altUrl : alternativeUrls) {
                        try {
                            Log.d(TAG, "🔗 Trying alternative URL: " + altUrl);
                            
                            DownloadInfo downloadInfo = startDirectDownload(altUrl, title, type, season, episode, embedUrl).get();
                            
                            // Test if download actually starts
                            Thread.sleep(2000); // Wait 2 seconds
                            updateDownloadProgress();
                            
                            if (downloadInfo.progress > 0 || "Downloading".equals(downloadInfo.status)) {
                                Log.d(TAG, "✓ Alternative URL working: " + altUrl);
                                return downloadInfo;
                            } else {
                                // Cancel this attempt and try next
                                downloadManager.remove(downloadInfo.downloadId);
                                activeDownloads.remove(downloadInfo.downloadId);
                            }
                            
                        } catch (Exception e) {
                            Log.w(TAG, "Alternative URL failed: " + altUrl + " - " + e.getMessage());
                        }
                    }
                }
                
                // Method 2: Use external downloader approach
                return useExternalDownloaderApproach(embedUrl, title, type, season, episode);
                
            } catch (Exception e) {
                Log.e(TAG, "All alternative methods failed: " + e.getMessage());
                throw new RuntimeException("All download methods failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Extract TMDB ID from embed URL
     */
    private String extractTmdbId(String embedUrl) {
        try {
            if (embedUrl.contains("vidlink.pro/movie/")) {
                return embedUrl.substring(embedUrl.lastIndexOf("/") + 1);
            } else if (embedUrl.contains("vidlink.pro/tv/")) {
                String[] parts = embedUrl.split("/");
                if (parts.length >= 5) {
                    return parts[4]; // Get TMDB ID from /tv/{id}/{season}/{episode}
                }
            } else if (embedUrl.contains("tmdb=")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("tmdb=([^&]+)");
                java.util.regex.Matcher matcher = pattern.matcher(embedUrl);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting TMDB ID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Build alternative stream URLs using known working patterns
     */
    private String[] buildAlternativeStreamUrls(String tmdbId, String type, String season, String episode) {
        List<String> urls = new ArrayList<>();
        
        if ("movie".equals(type)) {
            // Movie alternatives
            urls.add("https://vidsrc.to/embed/movie/" + tmdbId);
            urls.add("https://vidsrc.me/embed/movie/" + tmdbId);
            urls.add("https://2embed.to/embed/tmdb/movie?id=" + tmdbId);
            urls.add("https://multiembed.mov/directstream.php?video_id=" + tmdbId + "&tmdb=1");
            urls.add("https://www.2embed.cc/embed/tmdb/movie?id=" + tmdbId);
        } else {
            // TV show alternatives
            String s = season != null ? season : "1";
            String e = episode != null ? episode : "1";
            
            urls.add("https://vidsrc.to/embed/tv/" + tmdbId + "/" + s + "/" + e);
            urls.add("https://vidsrc.me/embed/tv/" + tmdbId + "/" + s + "/" + e);
            urls.add("https://2embed.to/embed/tmdb/tv?id=" + tmdbId + "&s=" + s + "&e=" + e);
            urls.add("https://multiembed.mov/directstream.php?video_id=" + tmdbId + "&tmdb=1&s=" + s + "&e=" + e);
            urls.add("https://www.2embed.cc/embed/tmdb/tv?id=" + tmdbId + "&s=" + s + "&e=" + e);
        }
        
        return urls.toArray(new String[0]);
    }
    
    /**
     * Use external downloader approach - create a downloadable link
     */
    private CompletableFuture<DownloadInfo> useExternalDownloaderApproach(String embedUrl, String title, String type, String season, String episode) {
        Log.d(TAG, "🔧 Using external downloader approach...");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create a special download URL that includes instructions
                String filename = generateFilename(title, type, season, episode);
                String instructionUrl = createInstructionUrl(embedUrl, title, filename);
                
                Log.d(TAG, "📝 Created instruction URL: " + instructionUrl);
                
                // Start download with instruction URL
                return startDirectDownload(instructionUrl, title, type, season, episode, embedUrl).get();
                
            } catch (Exception e) {
                Log.e(TAG, "External downloader approach failed: " + e.getMessage());
                throw new RuntimeException("External downloader failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Create an instruction URL that can be downloaded
     */
    private String createInstructionUrl(String embedUrl, String title, String filename) {
        // Create a data URL with download instructions
        String instructions = 
            "MTV Download Instructions\n" +
            "========================\n\n" +
            "Title: " + title + "\n" +
            "Filename: " + filename + "\n" +
            "Original URL: " + embedUrl + "\n\n" +
            "To download this video:\n" +
            "1. Open the original URL in a browser\n" +
            "2. Use a video downloader extension\n" +
            "3. Or use external download tools\n\n" +
            "Original URL: " + embedUrl;
        
        // Convert to base64 data URL
        String base64 = android.util.Base64.encodeToString(instructions.getBytes(), android.util.Base64.NO_WRAP);
        return "data:text/plain;base64," + base64;
    }
    
    /**
     * Start direct download using Android DownloadManager
     */
    private CompletableFuture<DownloadInfo> startDirectDownload(String streamUrl, String title, String type, 
                                                              String season, String episode, String originalUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = generateFilename(title, type, season, episode);
                
                Log.d(TAG, "📥 Starting direct download:");
                Log.d(TAG, "Stream URL: " + streamUrl);
                Log.d(TAG, "Filename: " + filename);
                
                // Create download request
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(streamUrl));
                
                // Set comprehensive headers
                request.addRequestHeader("User-Agent", 
                    "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                request.addRequestHeader("Accept", "*/*");
                request.addRequestHeader("Accept-Language", "en-US,en;q=0.9");
                request.addRequestHeader("Accept-Encoding", "identity");
                request.addRequestHeader("Connection", "keep-alive");
                request.addRequestHeader("DNT", "1");
                
                // Set referer based on stream URL
                if (streamUrl.contains("vidlink")) {
                    request.addRequestHeader("Referer", "https://vidlink.pro/");
                    request.addRequestHeader("Origin", "https://vidlink.pro");
                } else if (streamUrl.contains("vidsrc")) {
                    request.addRequestHeader("Referer", "https://vidsrc.to/");
                    request.addRequestHeader("Origin", "https://vidsrc.to");
                } else {
                    request.addRequestHeader("Referer", originalUrl);
                }
                
                // Set destination in app's private directory
                File downloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
                if (!downloadsDir.exists()) {
                    boolean created = downloadsDir.mkdirs();
                    Log.d(TAG, "Downloads directory created: " + created);
                }
                
                File destFile = new File(downloadsDir, filename);
                request.setDestinationUri(Uri.fromFile(destFile));
                
                // Configure download behavior
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setTitle("MTV: " + title);
                request.setDescription("Downloading " + filename);
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
                request.setAllowedOverRoaming(true);
                request.setAllowedOverMetered(true);
                
                // Start download
                long downloadId = downloadManager.enqueue(request);
                
                DownloadInfo downloadInfo = new DownloadInfo(downloadId, title, originalUrl, streamUrl, 
                                                           filename, type, season, episode);
                downloadInfo.filePath = destFile.getAbsolutePath();
                
                activeDownloads.put(downloadId, downloadInfo);
                
                // Notify listeners
                for (DownloadListener listener : listeners) {
                    listener.onDownloadStarted(downloadInfo);
                }
                
                Log.d(TAG, "✅ Download started: " + filename + " (ID: " + downloadId + ")");
                
                return downloadInfo;
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to start direct download: " + e.getMessage());
                throw new RuntimeException("Failed to start download: " + e.getMessage());
            }
        });
    }
    
    /**
     * Generate appropriate filename
     */
    private String generateFilename(String title, String type, String season, String episode) {
        String cleanTitle = title.replaceAll("[^a-zA-Z0-9\\s\\-]", "").trim().replaceAll("\\s+", "_");
        
        if ("tv".equals(type) && season != null && episode != null) {
            return String.format("%s_S%02dE%02d.mp4", cleanTitle, 
                Integer.parseInt(season), Integer.parseInt(episode));
        } else {
            return String.format("%s.mp4", cleanTitle);
        }
    }
    
    /**
     * Update download progress for all active downloads
     */
    private void updateDownloadProgress() {
        for (DownloadInfo download : activeDownloads.values()) {
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(download.downloadId);
            Cursor cursor = downloadManager.query(query);
            
            if (cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                
                int status = cursor.getInt(statusIndex);
                long downloaded = cursor.getLong(downloadedIndex);
                long total = cursor.getLong(totalIndex);
                
                if (total > 0) {
                    int progress = (int) ((downloaded * 100L) / total);
                    if (progress != download.progress) {
                        download.progress = progress;
                        
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadProgress(download, progress);
                        }
                    }
                }
                
                switch (status) {
                    case DownloadManager.STATUS_PENDING:
                        download.status = "Pending";
                        break;
                    case DownloadManager.STATUS_RUNNING:
                        download.status = "Downloading";
                        break;
                    case DownloadManager.STATUS_PAUSED:
                        download.status = "Paused";
                        break;
                    case DownloadManager.STATUS_SUCCESSFUL:
                        download.status = "Completed";
                        download.progress = 100;
                        break;
                    case DownloadManager.STATUS_FAILED:
                        download.status = "Failed";
                        int reason = cursor.getInt(reasonIndex);
                        Log.e(TAG, "Download failed with reason: " + getFailureReason(reason));
                        break;
                }
            }
            cursor.close();
        }
    }
    
    /**
     * Get human-readable failure reason
     */
    private String getFailureReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "Cannot resume download";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "Storage device not found";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "File already exists";
            case DownloadManager.ERROR_FILE_ERROR:
                return "File system error";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "HTTP data error - stream may be protected";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Insufficient storage space";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "Too many redirects";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "Server returned error code";
            case DownloadManager.ERROR_UNKNOWN:
            default:
                return "Stream is protected or unavailable for download";
        }
    }
    
    // Broadcast receiver for download completion
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            
            DownloadInfo downloadInfo = activeDownloads.get(downloadId);
            if (downloadInfo != null) {
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                Cursor cursor = downloadManager.query(query);
                
                if (cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIndex);
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloadInfo.status = "Completed";
                        downloadInfo.progress = 100;
                        
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadCompleted(downloadInfo, downloadInfo.filePath);
                        }
                        
                        Log.d(TAG, "✅ Download completed: " + downloadInfo.filename);
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        int reason = cursor.getInt(reasonIndex);
                        String error = getFailureReason(reason);
                        
                        downloadInfo.status = "Failed";
                        
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadFailed(downloadInfo, error);
                        }
                        
                        Log.e(TAG, "❌ Download failed: " + downloadInfo.filename + " - " + error);
                    }
                }
                cursor.close();
                
                if (downloadInfo.status.equals("Completed") || downloadInfo.status.equals("Failed")) {
                    activeDownloads.remove(downloadId);
                }
            }
        }
    };
    
    // Public methods for managing downloads
    public void addDownloadListener(DownloadListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeDownloadListener(DownloadListener listener) {
        listeners.remove(listener);
    }
    
    public List<DownloadInfo> getActiveDownloads() {
        updateDownloadProgress();
        return new ArrayList<>(activeDownloads.values());
    }
    
    public List<DownloadInfo> getCompletedDownloads() {
        List<DownloadInfo> completed = new ArrayList<>();
        
        File downloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
        if (downloadsDir.exists() && downloadsDir.isDirectory()) {
            File[] files = downloadsDir.listFiles((dir, name) -> 
                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".webm"));
            
            if (files != null) {
                for (File file : files) {
                    String filename = file.getName();
                    String title = extractTitleFromFilename(filename);
                    String[] seasonEpisode = extractSeasonEpisodeFromFilename(filename);
                    String type = seasonEpisode[0] != null ? "tv" : "movie";
                    
                    DownloadInfo info = new DownloadInfo(-1, title, "", "", filename, type, seasonEpisode[0], seasonEpisode[1]);
                    info.filePath = file.getAbsolutePath();
                    info.status = "Completed";
                    info.progress = 100;
                    
                    completed.add(info);
                }
            }
        }
        
        return completed;
    }
    
    public void cancelDownload(long downloadId) {
        downloadManager.remove(downloadId);
        activeDownloads.remove(downloadId);
        Log.d(TAG, "🚫 Download cancelled: " + downloadId);
    }
    
    private String extractTitleFromFilename(String filename) {
        String nameWithoutExt = filename.replaceFirst("\\.[^.]*$", "");
        String[] parts = nameWithoutExt.split("_");
        
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part.matches("S\\d+E\\d+") || part.matches("\\d+p")) {
                break;
            }
            if (title.length() > 0) title.append(" ");
            title.append(part.replace("_", " "));
        }
        
        return title.toString();
    }
    
    private String[] extractSeasonEpisodeFromFilename(String filename) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("S(\\d+)E(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(filename);
        
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        
        return new String[]{null, null};
    }
    
    public void cleanup() {
        try {
            context.unregisterReceiver(downloadReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver not registered");
        }
    }
}