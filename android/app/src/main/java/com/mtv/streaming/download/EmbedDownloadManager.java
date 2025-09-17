package com.mtv.streaming.download;

import android.app.DownloadManager.Request;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced download manager that can handle embed URLs
 * Uses WebView to extract actual stream URLs from embed pages
 */
public class EmbedDownloadManager {
    private static final String TAG = "EmbedDownloadManager";
    private static EmbedDownloadManager instance;
    private final Context context;
    private final android.app.DownloadManager downloadManager;
    private final Map<Long, DownloadInfo> activeDownloads;
    private final List<DownloadListener> listeners;
    private final Map<String, CompletableFuture<String>> pendingExtractions;
    
    public interface DownloadListener {
        void onDownloadStarted(DownloadInfo download);
        void onDownloadProgress(DownloadInfo download, int progress);
        void onDownloadCompleted(DownloadInfo download, String filePath);
        void onDownloadFailed(DownloadInfo download, String error);
    }
    
    public static class DownloadInfo {
        public final long downloadId;
        public final String title;
        public final String url;
        public final String filename;
        public final String season;
        public final String episode;
        public final String type; // "movie" or "tv"
        public int progress;
        public String status;
        public String filePath;
        
        public DownloadInfo(long downloadId, String title, String url, String filename, String type, String season, String episode) {
            this.downloadId = downloadId;
            this.title = title;
            this.url = url;
            this.filename = filename;
            this.type = type;
            this.season = season;
            this.episode = episode;
            this.progress = 0;
            this.status = "Starting";
        }
    }
    
    private EmbedDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        this.activeDownloads = new HashMap<>();
        this.listeners = new ArrayList<>();
        this.pendingExtractions = new ConcurrentHashMap<>();
        
        // Register broadcast receiver for download completion
        IntentFilter filter = new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        context.registerReceiver(downloadReceiver, filter);
    }
    
    public static synchronized EmbedDownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new EmbedDownloadManager(context);
        }
        return instance;
    }
    
    /**
     * Download video from embed URL
     */
    public CompletableFuture<DownloadInfo> downloadVideo(String embedUrl, String title, String type, String season, String episode) {
        Log.d(TAG, "Starting download for: " + title);
        Log.d(TAG, "Embed URL: " + embedUrl);
        
        return extractStreamUrl(embedUrl)
            .thenCompose(streamUrl -> {
                if (streamUrl == null || streamUrl.isEmpty()) {
                    // If we can't extract a direct URL, try downloading the embed page itself
                    Log.w(TAG, "Could not extract direct stream URL, using embed URL for download");
                    return startDirectDownload(embedUrl, title, type, season, episode);
                } else {
                    Log.d(TAG, "Extracted stream URL: " + streamUrl);
                    return startDirectDownload(streamUrl, title, type, season, episode);
                }
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "Download failed: " + throwable.getMessage());
                throw new RuntimeException("Download failed: " + throwable.getMessage());
            });
    }
    
    /**
     * Extract actual stream URL from embed page using WebView
     */
    private CompletableFuture<String> extractStreamUrl(String embedUrl) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // Check if we're already extracting this URL
        if (pendingExtractions.containsKey(embedUrl)) {
            return pendingExtractions.get(embedUrl);
        }
        
        pendingExtractions.put(embedUrl, future);
        
        // Run on main thread since WebView requires it
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                WebView webView = new WebView(context);
                
                // Configure WebView for stream extraction
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setLoadWithOverviewMode(true);
                webView.getSettings().setUseWideViewPort(true);
                webView.getSettings().setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
                );
                
                // Set up WebView client to intercept stream URLs
                webView.setWebViewClient(new WebViewClient() {
                    private boolean streamFound = false;
                    
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        
                        // Look for video stream URLs
                        if (!streamFound && isVideoUrl(url)) {
                            Log.d(TAG, "Found video stream: " + url);
                            streamFound = true;
                            future.complete(url);
                            
                            // Clean up WebView
                            mainHandler.post(() -> {
                                webView.destroy();
                                pendingExtractions.remove(embedUrl);
                            });
                            
                            return null;
                        }
                        
                        return super.shouldInterceptRequest(view, request);
                    }
                    
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        
                        // If no stream found after page load, try JavaScript extraction
                        if (!streamFound) {
                            // Wait a bit for dynamic content to load
                            mainHandler.postDelayed(() -> {
                                if (!streamFound) {
                                    extractWithJavaScript(view, future, embedUrl);
                                }
                            }, 3000);
                        }
                    }
                    
                    @Override
                    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                        super.onReceivedError(view, errorCode, description, failingUrl);
                        Log.w(TAG, "WebView error: " + description);
                        
                        if (!streamFound) {
                            // Still try to complete with the original embed URL
                            future.complete(embedUrl);
                            webView.destroy();
                            pendingExtractions.remove(embedUrl);
                        }
                    }
                });
                
                // Load the embed page
                Log.d(TAG, "Loading embed page in WebView: " + embedUrl);
                webView.loadUrl(embedUrl);
                
                // Set timeout
                mainHandler.postDelayed(() -> {
                    if (!future.isDone()) {
                        Log.w(TAG, "Stream extraction timeout, using embed URL");
                        future.complete(embedUrl);
                        webView.destroy();
                        pendingExtractions.remove(embedUrl);
                    }
                }, 15000); // 15 second timeout
                
            } catch (Exception e) {
                Log.e(TAG, "Error setting up WebView: " + e.getMessage());
                future.complete(embedUrl); // Fallback to embed URL
                pendingExtractions.remove(embedUrl);
            }
        });
        
        return future;
    }
    
    /**
     * Try to extract stream URL using JavaScript
     */
    private void extractWithJavaScript(WebView webView, CompletableFuture<String> future, String embedUrl) {
        // JavaScript to find video elements and their sources
        String javascript = 
            "(function() {" +
            "  var videos = document.querySelectorAll('video');" +
            "  for (var i = 0; i < videos.length; i++) {" +
            "    if (videos[i].src && videos[i].src.length > 0) {" +
            "      return videos[i].src;" +
            "    }" +
            "  }" +
            "  var sources = document.querySelectorAll('source');" +
            "  for (var i = 0; i < sources.length; i++) {" +
            "    if (sources[i].src && sources[i].src.length > 0) {" +
            "      return sources[i].src;" +
            "    }" +
            "  }" +
            "  return null;" +
            "})()";
        
        webView.evaluateJavascript(javascript, result -> {
            if (result != null && !result.equals("null") && !result.equals("\"\"")) {
                String cleanUrl = result.replace("\"", "");
                if (isVideoUrl(cleanUrl)) {
                    Log.d(TAG, "JavaScript found video URL: " + cleanUrl);
                    future.complete(cleanUrl);
                } else {
                    future.complete(embedUrl);
                }
            } else {
                Log.d(TAG, "JavaScript extraction failed, using embed URL");
                future.complete(embedUrl);
            }
            
            webView.destroy();
            pendingExtractions.remove(embedUrl);
        });
    }
    
    /**
     * Check if URL is a video stream URL
     */
    private boolean isVideoUrl(String url) {
        if (url == null) return false;
        
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".mp4") || 
               lowerUrl.contains(".m3u8") || 
               lowerUrl.contains(".mkv") || 
               lowerUrl.contains(".avi") || 
               lowerUrl.contains(".webm") ||
               (lowerUrl.contains("stream") && (lowerUrl.contains("http") || lowerUrl.contains("https"))) ||
               lowerUrl.contains("video/") ||
               lowerUrl.contains("application/x-mpegurl");
    }
    
    /**
     * Start direct download with Android DownloadManager
     */
    private CompletableFuture<DownloadInfo> startDirectDownload(String url, String title, String type, String season, String episode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = generateFilename(title, type, season, episode);
                
                Log.d(TAG, "Starting direct download:");
                Log.d(TAG, "URL: " + url);
                Log.d(TAG, "Filename: " + filename);
                
                // Create download request
                Request request = new Request(Uri.parse(url));
                
                // Set headers for better compatibility
                request.addRequestHeader("User-Agent", 
                    "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                request.addRequestHeader("Accept", "*/*");
                request.addRequestHeader("Accept-Language", "en-US,en;q=0.9");
                
                // Set referer based on URL
                if (url.contains("vidlink")) {
                    request.addRequestHeader("Referer", "https://vidlink.pro/");
                } else if (url.contains("vidsrc")) {
                    request.addRequestHeader("Referer", "https://vidsrc.to/");
                }
                
                // Set destination
                File downloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                
                File destFile = new File(downloadsDir, filename);
                request.setDestinationUri(Uri.fromFile(destFile));
                
                // Set notification and visibility
                request.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setTitle("Downloading " + title);
                request.setDescription(filename);
                
                // Allow download over mobile and WiFi
                request.setAllowedNetworkTypes(Request.NETWORK_MOBILE | Request.NETWORK_WIFI);
                request.setAllowedOverRoaming(true);
                
                // Start download
                long downloadId = downloadManager.enqueue(request);
                
                DownloadInfo downloadInfo = new DownloadInfo(downloadId, title, url, filename, type, season, episode);
                downloadInfo.filePath = destFile.getAbsolutePath();
                
                activeDownloads.put(downloadId, downloadInfo);
                
                // Notify listeners
                for (DownloadListener listener : listeners) {
                    listener.onDownloadStarted(downloadInfo);
                }
                
                Log.d(TAG, "Download started successfully: " + filename + " (ID: " + downloadId + ")");
                
                return downloadInfo;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to start download: " + e.getMessage());
                throw new RuntimeException("Failed to start download: " + e.getMessage());
            }
        });
    }
    
    /**
     * Generate appropriate filename for download
     */
    private String generateFilename(String title, String type, String season, String episode) {
        String cleanTitle = title.replaceAll("[^a-zA-Z0-9\\s]", "").trim().replaceAll("\\s+", "_");
        
        if ("tv".equals(type) && season != null && episode != null) {
            return String.format("%s_S%sE%s.mp4", cleanTitle, 
                String.format("%02d", Integer.parseInt(season)), 
                String.format("%02d", Integer.parseInt(episode)));
        } else {
            return String.format("%s.mp4", cleanTitle);
        }
    }
    
    // Rest of the methods remain the same as the original DownloadManager
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
                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi"));
            
            if (files != null) {
                for (File file : files) {
                    String filename = file.getName();
                    String title = extractTitleFromFilename(filename);
                    String[] seasonEpisode = extractSeasonEpisodeFromFilename(filename);
                    String type = seasonEpisode[0] != null ? "tv" : "movie";
                    
                    DownloadInfo info = new DownloadInfo(-1, title, "", filename, type, seasonEpisode[0], seasonEpisode[1]);
                    info.filePath = file.getAbsolutePath();
                    info.status = "Completed";
                    info.progress = 100;
                    
                    completed.add(info);
                }
            }
        }
        
        return completed;
    }
    
    private void updateDownloadProgress() {
        for (DownloadInfo download : activeDownloads.values()) {
            Cursor cursor = downloadManager.query(new android.app.DownloadManager.Query().setFilterById(download.downloadId));
            
            if (cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                int downloadedIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                
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
                    case android.app.DownloadManager.STATUS_PENDING:
                        download.status = "Pending";
                        break;
                    case android.app.DownloadManager.STATUS_RUNNING:
                        download.status = "Downloading";
                        break;
                    case android.app.DownloadManager.STATUS_PAUSED:
                        download.status = "Paused";
                        break;
                }
            }
            cursor.close();
        }
    }
    
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long downloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            
            DownloadInfo downloadInfo = activeDownloads.get(downloadId);
            if (downloadInfo != null) {
                Cursor cursor = downloadManager.query(new android.app.DownloadManager.Query().setFilterById(downloadId));
                
                if (cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIndex);
                    
                    if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                        downloadInfo.status = "Completed";
                        downloadInfo.progress = 100;
                        
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadCompleted(downloadInfo, downloadInfo.filePath);
                        }
                        
                        Log.d(TAG, "Download completed: " + downloadInfo.filename);
                    } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                        downloadInfo.status = "Failed";
                        
                        int reasonIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON);
                        int reason = cursor.getInt(reasonIndex);
                        String error = getFailureReason(reason);
                        
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadFailed(downloadInfo, error);
                        }
                        
                        Log.e(TAG, "Download failed: " + downloadInfo.filename + " - " + error);
                    }
                }
                cursor.close();
                
                if (downloadInfo.status.equals("Completed") || downloadInfo.status.equals("Failed")) {
                    activeDownloads.remove(downloadId);
                }
            }
        }
    };
    
    private String getFailureReason(int reason) {
        switch (reason) {
            case android.app.DownloadManager.ERROR_CANNOT_RESUME:
                return "Cannot resume download";
            case android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "Device not found";
            case android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "File already exists";
            case android.app.DownloadManager.ERROR_FILE_ERROR:
                return "File error";
            case android.app.DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "HTTP data error";
            case android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Insufficient storage space";
            case android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "Too many redirects";
            case android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "Unhandled HTTP code";
            case android.app.DownloadManager.ERROR_UNKNOWN:
            default:
                return "Unknown error";
        }
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
    
    public void cancelDownload(long downloadId) {
        downloadManager.remove(downloadId);
        activeDownloads.remove(downloadId);
    }
    
    public void cleanup() {
        try {
            context.unregisterReceiver(downloadReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver not registered");
        }
        
        // Clean up any pending extractions
        for (CompletableFuture<String> future : pendingExtractions.values()) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        pendingExtractions.clear();
    }
}