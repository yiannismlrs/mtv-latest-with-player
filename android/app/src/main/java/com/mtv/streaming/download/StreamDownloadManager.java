package com.mtv.streaming.download;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private final AccessRestrictionHandler restrictionHandler;
    
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
        this.restrictionHandler = new AccessRestrictionHandler(this.context);
        
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
        if (lower.matches(".*\\\\.(mp4|m3u8|mkv|avi|webm)\\\\?.*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if content is an HLS manifest
     */
    private boolean isHLSManifest(String content) {
        return content != null && 
               (content.contains("#EXTM3U") || 
                content.contains("#EXT-X-STREAM-INF") || 
                content.contains("#EXT-X-TARGETDURATION"));
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
     * Try alternative download methods when direct extraction fails (OnStream-style)
     */
    private CompletableFuture<DownloadInfo> tryAlternativeDownloadMethods(String embedUrl, String title, String type, String season, String episode) {
        Log.d(TAG, "🔄 Trying OnStream-style alternative download methods...");
        
        CompletableFuture<DownloadInfo> future = new CompletableFuture<>();
        
        CompletableFuture.runAsync(() -> {
            try {
                // First check if original URL has access restrictions
                restrictionHandler.detectRestrictions(embedUrl)
                    .thenAccept(restrictionInfo -> {
                        if (restrictionInfo.type != null) {
                            Log.d(TAG, "🚫 Restriction detected: " + restrictionInfo.type + " - " + restrictionInfo.message);
                            Log.d(TAG, "💡 Suggestion: " + restrictionInfo.suggestedAction);
                            
                            // Notify user about the restriction
                            for (DownloadListener listener : listeners) {
                                DownloadInfo restrictionDownload = new DownloadInfo(-3, title, embedUrl, embedUrl, 
                                    "restriction_detected.txt", type, season, episode);
                                restrictionDownload.status = "Restricted: " + restrictionInfo.type.toString();
                                listener.onDownloadFailed(restrictionDownload, 
                                    restrictionInfo.message + "\n\n" + restrictionHandler.getRestrictionGuidance(restrictionInfo.type));
                            }
                        }
                    })
                    .join(); // Wait for restriction check
                
                // Method 1: Try known working stream URLs based on TMDB ID
                String tmdbId = extractTmdbId(embedUrl);
                if (tmdbId != null) {
                    Log.d(TAG, "📋 Extracted TMDB ID: " + tmdbId);
                    
                    String[] alternativeUrls = buildAlternativeStreamUrls(tmdbId, type, season, episode);
                    
                    for (String altUrl : alternativeUrls) {
                        try {
                            Log.d(TAG, "🔗 Testing alternative source: " + altUrl.substring(0, Math.min(50, altUrl.length())) + "...");
                            
                            // Check if this alternative source is accessible
                            AccessRestrictionHandler.RestrictionInfo altRestriction = 
                                restrictionHandler.detectRestrictions(altUrl).get();
                            
                            if (altRestriction.type != null) {
                                Log.d(TAG, "⚠️ Alternative also restricted: " + altRestriction.type);
                                continue; // Try next alternative
                            }
                            
                            Log.d(TAG, "✓ Alternative source accessible, starting download...");
                            
                            DownloadInfo downloadInfo = startDirectDownload(altUrl, title, type, season, episode, embedUrl).get();
                            
                            // Test if download actually starts
                            Thread.sleep(3000); // Wait 3 seconds for download to initialize
                            updateDownloadProgress();
                            
                            if (downloadInfo.progress > 0 || "Downloading".equals(downloadInfo.status) || "Processing HLS".equals(downloadInfo.status)) {
                                Log.d(TAG, "✓ Alternative URL working: " + altUrl.substring(0, Math.min(50, altUrl.length())));
                                future.complete(downloadInfo);
                                return;
                            } else {
                                // Cancel this attempt and try next
                                if (downloadInfo.downloadId > 0) {
                                    downloadManager.remove(downloadInfo.downloadId);
                                    activeDownloads.remove(downloadInfo.downloadId);
                                }
                                Log.d(TAG, "❌ Alternative not working, trying next...");
                            }
                            
                        } catch (Exception e) {
                            Log.w(TAG, "Alternative URL failed: " + e.getMessage());
                        }
                    }
                }
                
                // Method 2: Try external downloader integration
                Log.d(TAG, "🔧 Trying external downloader integration...");
                boolean externalSuccess = restrictionHandler.openExternalDownloader(embedUrl);
                
                if (externalSuccess) {
                    // Create a pseudo download info for external downloader
                    DownloadInfo externalDownloadInfo = new DownloadInfo(-4, title, embedUrl, embedUrl, 
                        "external_download.txt", type, season, episode);
                    externalDownloadInfo.status = "Redirected to External Downloader";
                    externalDownloadInfo.progress = 0;
                    
                    for (DownloadListener listener : listeners) {
                        listener.onDownloadCompleted(externalDownloadInfo, "External downloader opened");
                    }
                    
                    future.complete(externalDownloadInfo);
                    return;
                }
                
                // Method 3: Provide user guidance for manual download
                Log.d(TAG, "📝 Creating download instructions for user...");
                DownloadInfo result = createDownloadInstructions(embedUrl, title, type, season, episode).get();
                future.complete(result);
                
            } catch (Exception e) {
                Log.e(TAG, "All alternative methods failed: " + e.getMessage());
                
                // Final fallback - create instruction file
                try {
                    DownloadInfo instructionInfo = createDownloadInstructions(embedUrl, title, type, season, episode).get();
                    future.complete(instructionInfo);
                } catch (Exception finalException) {
                    future.completeExceptionally(new RuntimeException("All download methods failed: " + e.getMessage()));
                }
            }
        });
        
        return future;
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
     * Build alternative stream URLs using known working patterns (OnStream-style)
     */
    private String[] buildAlternativeStreamUrls(String tmdbId, String type, String season, String episode) {
        List<String> urls = new ArrayList<>();
        
        if ("movie".equals(type)) {
            // Movie alternatives - ordered by reliability
            urls.add("https://vidsrc.to/embed/movie/" + tmdbId);
            urls.add("https://vidsrc.cc/v2/embed/movie/" + tmdbId);
            urls.add("https://vidsrc.me/embed/movie/" + tmdbId);
            urls.add("https://2embed.to/embed/tmdb/movie?id=" + tmdbId);
            urls.add("https://www.2embed.cc/embed/tmdb/movie?id=" + tmdbId);
            urls.add("https://multiembed.mov/directstream.php?video_id=" + tmdbId + "&tmdb=1");
            urls.add("https://embed.su/embed/movie/" + tmdbId);
            urls.add("https://autoembed.to/movie/tmdb/" + tmdbId);
            urls.add("https://player.smashy.stream/movie/" + tmdbId);
            
            // Backup sources with different formats
            urls.add("https://superembed.stream/movie/" + tmdbId);
            urls.add("https://embedder.net/e/movie?tmdb=" + tmdbId);
            
        } else {
            // TV show alternatives - ordered by reliability
            String s = season != null ? season : "1";
            String e = episode != null ? episode : "1";
            
            urls.add("https://vidsrc.to/embed/tv/" + tmdbId + "/" + s + "/" + e);
            urls.add("https://vidsrc.cc/v2/embed/tv/" + tmdbId + "/" + s + "/" + e);
            urls.add("https://vidsrc.me/embed/tv/" + tmdbId + "/" + s + "/" + e);
            urls.add("https://2embed.to/embed/tmdb/tv?id=" + tmdbId + "&s=" + s + "&e=" + e);
            urls.add("https://www.2embed.cc/embed/tmdb/tv?id=" + tmdbId + "&s=" + s + "&e=" + e);
            urls.add("https://multiembed.mov/directstream.php?video_id=" + tmdbId + "&tmdb=1&s=" + s + "&e=" + e);
            urls.add("https://embed.su/embed/tv/" + tmdbId + "/" + s + "/" + e);
            urls.add("https://autoembed.to/tv/tmdb/" + tmdbId + "-" + s + "-" + e);
            urls.add("https://player.smashy.stream/tv/" + tmdbId + "/" + s + "/" + e);
            
            // Backup sources with different formats
            urls.add("https://superembed.stream/tv/" + tmdbId + "/" + s + "/" + e);
            urls.add("https://embedder.net/e/tv?tmdb=" + tmdbId + "&season=" + s + "&episode=" + e);
        }
        
        // Add region-specific mirrors as fallback
        if ("movie".equals(type)) {
            urls.add("https://streamm4u.ws/movie/" + tmdbId);
            urls.add("https://movies7.to/movie/" + tmdbId);
        } else {
            String s = season != null ? season : "1";
            String e = episode != null ? episode : "1";
            urls.add("https://streamm4u.ws/tv/" + tmdbId + "-" + s + "-" + e);
            urls.add("https://movies7.to/tv/" + tmdbId + "-" + s + "-" + e);
        }
        
        return urls.toArray(new String[0]);
    }
    
    /**
     * Create comprehensive download instructions when all methods fail
     */
    private CompletableFuture<DownloadInfo> createDownloadInstructions(String embedUrl, String title, String type, String season, String episode) {
        Log.d(TAG, "📝 Creating comprehensive download instructions...");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = title.replaceAll("[^a-zA-Z0-9\\s\\-]", "").trim().replaceAll("\\s+", "_") + "_instructions.txt";
                String tmdbId = extractTmdbId(embedUrl);
                
                // Create comprehensive instructions
                StringBuilder instructions = new StringBuilder();
                instructions.append("MTV App - Download Instructions\n");
                instructions.append("=====================================\n\n");
                instructions.append("Content: ").append(title).append("\n");
                instructions.append("Type: ").append(type.toUpperCase()).append("\n");
                if ("tv".equals(type) && season != null && episode != null) {
                    instructions.append("Season: ").append(season).append(", Episode: ").append(episode).append("\n");
                }
                if (tmdbId != null) {
                    instructions.append("TMDB ID: ").append(tmdbId).append("\n");
                }
                instructions.append("Original URL: ").append(embedUrl).append("\n\n");
                
                instructions.append("DOWNLOAD OPTIONS:\n");
                instructions.append("================\n\n");
                
                instructions.append("Option 1 - Alternative Streaming Sources:\n");
                if (tmdbId != null) {
                    String[] alternatives = buildAlternativeStreamUrls(tmdbId, type, season, episode);
                    for (int i = 0; i < Math.min(5, alternatives.length); i++) {
                        instructions.append("  ").append(i + 1).append(". ").append(alternatives[i]).append("\n");
                    }
                }
                instructions.append("\n");
                
                instructions.append("Option 2 - External Download Managers:\n");
                instructions.append("  • ADM (Advanced Download Manager)\n");
                instructions.append("  • IDM+ (Internet Download Manager)\n");
                instructions.append("  • Turbo Download Manager\n");
                instructions.append("  Install from Google Play Store\n\n");
                
                instructions.append("Option 3 - VPN for Geo-Restrictions:\n");
                instructions.append("  • NordVPN (Recommended)\n");
                instructions.append("  • ExpressVPN\n");
                instructions.append("  • Free VPN apps from Play Store\n\n");
                
                instructions.append("Option 4 - Browser Extensions:\n");
                instructions.append("  • Video DownloadHelper\n");
                instructions.append("  • Flash Video Downloader\n");
                instructions.append("  • Stream Recorder\n\n");
                
                instructions.append("MANUAL STEPS:\n");
                instructions.append("=============\n");
                instructions.append("1. Open one of the alternative URLs in Chrome\n");
                instructions.append("2. Play the video and right-click > 'Save video as'\n");
                instructions.append("3. Or use browser developer tools (F12) to find video URL\n");
                instructions.append("4. If geo-blocked, enable VPN first\n\n");
                
                instructions.append("TROUBLESHOOTING:\n");
                instructions.append("================\n");
                instructions.append("• If sites are blocked: Use VPN or mobile data\n");
                instructions.append("• If captcha required: Complete manually in browser\n");
                instructions.append("• If no video found: Try different sources above\n");
                instructions.append("• For TV shows: Verify season/episode numbers\n\n");
                
                instructions.append("Generated by MTV App on: ").append(new java.util.Date().toString()).append("\n");
                
                // Save instructions to file
                File downloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MTV_Instructions");
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                
                File instructionsFile = new File(downloadsDir, filename);
                java.io.FileWriter writer = new java.io.FileWriter(instructionsFile);
                writer.write(instructions.toString());
                writer.close();
                
                // Create download info
                DownloadInfo downloadInfo = new DownloadInfo(-5, title, embedUrl, embedUrl, 
                    filename, type, season, episode);
                downloadInfo.status = "Instructions Created";
                downloadInfo.progress = 100;
                downloadInfo.filePath = instructionsFile.getAbsolutePath();
                
                Log.d(TAG, "✓ Download instructions created: " + filename);
                return downloadInfo;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to create instructions: " + e.getMessage());
                throw new RuntimeException("Failed to create instructions: " + e.getMessage());
            }
        });
    }
    
    /**
     * Enhanced HLS stream detection
     */
    private boolean isHLSStream(String streamUrl) {
        if (streamUrl == null || streamUrl.isEmpty()) {
            return false;
        }
        
        String lower = streamUrl.toLowerCase();
        
        // Check for M3U8 URLs
        if (lower.contains(".m3u8") || lower.contains("m3u8?")) {
            return true;
        }
        
        // Check for M3U8 content
        if (streamUrl.contains("#EXTM3U") || streamUrl.contains("#EXT-X-")) {
            return true;
        }
        
        // Check for HLS indicators in URL
        if (lower.contains("hls") && (lower.contains("stream") || lower.contains("playlist"))) {
            return true;
        }
        
        // Check for common HLS streaming patterns
        if (lower.matches(".*/playlist\\.m3u8.*") || lower.matches(".*/master\\.m3u8.*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Start direct download using Android DownloadManager
     */
    private CompletableFuture<DownloadInfo> startDirectDownload(String streamUrl, String title, String type, 
                                                              String season, String episode, String originalUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Enhanced HLS detection
                if (isHLSStream(streamUrl)) {
                    Log.d(TAG, "🎬 HLS stream detected, using HLS downloader");
                    Log.d(TAG, "Stream URL: " + streamUrl.substring(0, Math.min(200, streamUrl.length())) + "...");
                    return handleHLSDownload(streamUrl, title, type, season, episode, originalUrl).get();
                }
                
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
     * Handle HLS manifest download and conversion
     */
    private CompletableFuture<DownloadInfo> handleHLSDownload(String streamUrl, String title, String type, String season, String episode, String originalUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "🎬 Processing HLS download for: " + title);
                
                String filename = generateFilename(title, type, season, episode);
                
                // Create a pseudo download info for tracking
                DownloadInfo downloadInfo = new DownloadInfo(-1, title, originalUrl, streamUrl, 
                                                            filename, type, season, episode);
                
                File downloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                
                File destFile = new File(downloadsDir, filename);
                downloadInfo.filePath = destFile.getAbsolutePath();
                downloadInfo.status = "Processing HLS";
                
                // Notify listeners that download started
                for (DownloadListener listener : listeners) {
                    listener.onDownloadStarted(downloadInfo);
                }
                
                // Make variables effectively final for lambda
                final String finalStreamUrl = streamUrl;
                final String finalOriginalUrl = originalUrl;
                final String finalTitle = title;
                
                // Start HLS processing in background
                CompletableFuture.runAsync(() -> {
                    try {
                        String m3u8Content;
                        String baseUrl;
                        
                        // Check if streamUrl is already M3U8 content or a URL
                        if (finalStreamUrl.contains("#EXTM3U")) {
                            m3u8Content = finalStreamUrl;
                            baseUrl = finalOriginalUrl; // Use original URL as base
                        } else {
                            // Fetch M3U8 content from URL
                            Log.d(TAG, "Fetching M3U8 content from: " + finalStreamUrl);
                            m3u8Content = fetchM3U8Content(finalStreamUrl);
                            baseUrl = finalStreamUrl;
                            if (m3u8Content == null || m3u8Content.isEmpty()) {
                                throw new Exception("Failed to fetch M3U8 content - URL may be invalid or protected");
                            }
                            Log.d(TAG, "M3U8 content fetched, length: " + m3u8Content.length());
                        }
                        
                        // Use HLS downloader to process the manifest
                        HLSDownloader hlsDownloader = HLSDownloader.getInstance(context);
                        Bundle headers = new Bundle();
                        headers.putString("Referer", "https://vidlink.pro/");
                        headers.putString("Origin", "https://vidlink.pro");
                        
                        String outputPath = hlsDownloader.downloadHLS(m3u8Content, finalTitle, baseUrl, headers).get();
                        
                        // Update download info
                        downloadInfo.status = "Completed";
                        downloadInfo.progress = 100;
                        downloadInfo.filePath = outputPath;
                        
                        // Notify completion
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadCompleted(downloadInfo, outputPath);
                        }
                        
                        Log.d(TAG, "✅ HLS download completed: " + outputPath);
                        
                        } catch (Exception e) {
                            Log.e(TAG, "❌ HLS download failed: " + e.getMessage());
                            Log.d(TAG, "🎥 Using SPlayer-optimized HLS playlist download");
                        
                        try {
                            // SPlayer-optimized approach: Download M3U8 playlist for SPlayer
                            String m3u8Filename = finalTitle.replaceAll("[^a-zA-Z0-9\\s\\-]", "").trim().replaceAll("\\s+", "_") + "_SPlayer.m3u8";
                            
                            // Get the M3U8 content from the outer scope or use the stream URL
                            String contentForSPlayer;
                            if (finalStreamUrl.contains("#EXTM3U")) {
                                contentForSPlayer = finalStreamUrl; // It's already M3U8 content
                            } else {
                                // Try to fetch M3U8 content, but don't fail if it doesn't work
                                String fetchedContent = fetchM3U8Content(finalStreamUrl);
                                contentForSPlayer = fetchedContent != null ? fetchedContent : finalStreamUrl;
                            }
                            
                            // Create enhanced M3U8 content for SPlayer
                            String enhancedM3U8Content = createSPlayerOptimizedM3U8(contentForSPlayer, finalStreamUrl);
                            
                            // Save M3U8 file directly instead of downloading
                            File splayerDownloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
                            if (!splayerDownloadsDir.exists()) {
                                splayerDownloadsDir.mkdirs();
                            }
                            
                            File m3u8File = new File(splayerDownloadsDir, m3u8Filename);
                            
                            // Write enhanced M3U8 content to file
                            java.io.FileWriter writer = new java.io.FileWriter(m3u8File);
                            writer.write(enhancedM3U8Content);
                            writer.close();
                            
                            // Create download info for the saved playlist
                            DownloadInfo playlistDownloadInfo = new DownloadInfo(-2, finalTitle, finalOriginalUrl, finalStreamUrl, 
                                                                               m3u8Filename, downloadInfo.type, downloadInfo.season, downloadInfo.episode);
                            playlistDownloadInfo.status = "Ready for SPlayer";
                            playlistDownloadInfo.progress = 100;
                            playlistDownloadInfo.filePath = m3u8File.getAbsolutePath();
                            
                            // Notify listeners of completion immediately
                            for (DownloadListener listener : listeners) {
                                listener.onDownloadCompleted(playlistDownloadInfo, m3u8File.getAbsolutePath());
                            }
                            
                            Log.d(TAG, "✅ SPlayer playlist created: " + m3u8Filename);
                            Log.d(TAG, "🎥 File ready for SPlayer: " + m3u8File.getAbsolutePath());
                            
                        } catch (Exception fallbackError) {
                            Log.e(TAG, "❌ Fallback also failed: " + fallbackError.getMessage());
                            
                            downloadInfo.status = "Failed";
                            
                            for (DownloadListener listener : listeners) {
                                listener.onDownloadFailed(downloadInfo, "HLS processing failed - " + e.getMessage() + ". Try using an external downloader.");
                            }
                        }
                    }
                });
                
                return downloadInfo;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to start HLS download: " + e.getMessage());
                throw new RuntimeException("Failed to start HLS download: " + e.getMessage());
            }
        });
    }
    
    /**
     * Fetch M3U8 content from URL
     */
    private String fetchM3U8Content(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            
            if (url.contains("vidlink")) {
                connection.setRequestProperty("Referer", "https://vidlink.pro/");
            } else if (url.contains("vidsrc")) {
                connection.setRequestProperty("Referer", "https://vidsrc.to/");
            }
            
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            connection.disconnect();
            
            return content.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching M3U8: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create SPlayer-optimized M3U8 content
     */
    private String createSPlayerOptimizedM3U8(String originalM3U8Content, String baseUrl) {
        try {
            Log.d(TAG, "Creating SPlayer-optimized M3U8 content");
            
            if (originalM3U8Content.contains("#EXTM3U")) {
                // Already M3U8 content - enhance it for SPlayer
                StringBuilder enhanced = new StringBuilder();
                enhanced.append("# MTV App - SPlayer Compatible Playlist\n");
                enhanced.append("# Original source: ").append(baseUrl).append("\n");
                enhanced.append("# Date: ").append(new java.util.Date().toString()).append("\n\n");
                enhanced.append(originalM3U8Content);
                
                // Fix relative URLs to absolute URLs if needed
                String content = enhanced.toString();
                if (content.contains("/proxy/") && baseUrl.contains("vidlink.pro")) {
                    // Convert relative proxy URLs to absolute URLs with proper base
                    content = content.replaceAll("/proxy/", "https://vidlink.pro/proxy/");
                }
                
                return content;
            } else {
                // Create M3U8 from URL
                StringBuilder m3u8 = new StringBuilder();
                m3u8.append("#EXTM3U\n");
                m3u8.append("# MTV App - SPlayer Compatible Stream\n");
                m3u8.append("# Original URL: ").append(baseUrl).append("\n");
                m3u8.append("# Date: ").append(new java.util.Date().toString()).append("\n");
                m3u8.append("# Note: This playlist contains the direct stream URL\n");
                m3u8.append("# SPlayer will handle the HLS processing automatically\n\n");
                
                // Add stream info
                m3u8.append("#EXT-X-VERSION:3\n");
                m3u8.append("#EXT-X-TARGETDURATION:10\n");
                m3u8.append("#EXT-X-MEDIA-SEQUENCE:0\n\n");
                
                // Add the stream URL as a single "segment"
                m3u8.append("#EXTINF:3600.0,MTV Stream\n");
                m3u8.append(originalM3U8Content).append("\n");
                m3u8.append("#EXT-X-ENDLIST\n");
                
                return m3u8.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error creating SPlayer M3U8: " + e.getMessage());
            
            // Fallback: Simple M3U8 with direct URL
            return "#EXTM3U\n" +
                   "# MTV App - Fallback Playlist\n" +
                   "# Stream URL: " + baseUrl + "\n\n" +
                   "#EXT-X-VERSION:3\n" +
                   "#EXT-X-TARGETDURATION:10\n" +
                   "#EXTINF:3600.0,MTV Stream\n" +
                   baseUrl + "\n" +
                   "#EXT-X-ENDLIST\n";
        }
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
        
        // Check MTV Downloads directory for videos and playlists
        File downloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
        if (downloadsDir.exists() && downloadsDir.isDirectory()) {
            File[] files = downloadsDir.listFiles((dir, name) -> 
                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || 
                name.endsWith(".webm") || name.endsWith(".m3u8"));
            
            if (files != null) {
                for (File file : files) {
                    String filename = file.getName();
                    String title = extractTitleFromFilename(filename);
                    String[] seasonEpisode = extractSeasonEpisodeFromFilename(filename);
                    String type = seasonEpisode[0] != null ? "tv" : "movie";
                    
                    DownloadInfo info = new DownloadInfo(-1, title, "", "", filename, type, seasonEpisode[0], seasonEpisode[1]);
                    info.filePath = file.getAbsolutePath();
                    
                    if (filename.endsWith(".m3u8")) {
                        info.status = "HLS Playlist (SPlayer)";
                    } else {
                        info.status = "Video File";
                    }
                    
                    info.progress = 100;
                    completed.add(info);
                }
            }
        }
        
        // Check MTV Instructions directory for download guides
        File instructionsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MTV_Instructions");
        if (instructionsDir.exists() && instructionsDir.isDirectory()) {
            File[] instructionFiles = instructionsDir.listFiles((dir, name) -> name.endsWith("_instructions.txt"));
            
            if (instructionFiles != null) {
                for (File file : instructionFiles) {
                    String filename = file.getName();
                    String title = filename.replace("_instructions.txt", "").replace("_", " ");
                    
                    DownloadInfo info = new DownloadInfo(-1, title, "", "", filename, "instruction", null, null);
                    info.filePath = file.getAbsolutePath();
                    info.status = "Download Instructions";
                    info.progress = 100;
                    
                    completed.add(info);
                }
            }
        }
        
        // Sort by modification date (newest first)
        completed.sort((a, b) -> {
            try {
                File fileA = new File(a.filePath);
                File fileB = new File(b.filePath);
                return Long.compare(fileB.lastModified(), fileA.lastModified());
            } catch (Exception e) {
                return 0;
            }
        });
        
        return completed;
    }
    
    public void cancelDownload(long downloadId) {
        downloadManager.remove(downloadId);
        activeDownloads.remove(downloadId);
        Log.d(TAG, "🚫 Download cancelled: " + downloadId);
    }
    
    private String extractTitleFromFilename(String filename) {
        String nameWithoutExt = filename.replaceFirst("\\\\.[^.]*$", "");
        String[] parts = nameWithoutExt.split("_");
        
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part.matches("S\\\\d+E\\\\d+") || part.matches("\\\\d+p")) {
                break;
            }
            if (title.length() > 0) title.append(" ");
            title.append(part.replace("_", " "));
        }
        
        return title.toString();
    }
    
    private String[] extractSeasonEpisodeFromFilename(String filename) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("S(\\\\d+)E(\\\\d+)");
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