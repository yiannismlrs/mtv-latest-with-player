package com.mtv.streaming.providers;

import android.os.Bundle;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * SuperStreamProvider - Secondary stream provider
 * Demonstrates multi-provider system following Vega pattern
 */
public class SuperStreamProvider implements Provider {
    private static final String TAG = "SuperStreamProvider";
    private static final String BASE_URL = "https://embed.su/embed";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    public ProviderInfo getProviderInfo() {
        return new ProviderInfo(
            "superstream", 
            "SuperStream", 
            "1.0.0", 
            "Secondary streaming provider using embed.su", 
            true
        );
    }
    
    @Override
    public int getPriority() {
        return 2; // Second priority
    }
    
    @Override
    public CompletableFuture<StreamResult> resolveStreams(MediaInfo mediaInfo) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                Log.d(TAG, "Resolving streams for: " + mediaInfo.title);
                
                // Build embed URL
                String embedUrl = buildEmbedUrl(mediaInfo);
                Log.d(TAG, "Embed URL: " + embedUrl);
                
                // Extract stream URLs
                List<StreamSource> sources = extractStreams(embedUrl);
                
                long resolveTime = System.currentTimeMillis() - startTime;
                
                if (!sources.isEmpty()) {
                    Log.d(TAG, "Found " + sources.size() + " sources");
                    return new StreamResult(sources, resolveTime);
                } else {
                    return new StreamResult("No streams found", resolveTime);
                }
                
            } catch (Exception e) {
                long resolveTime = System.currentTimeMillis() - startTime;
                Log.e(TAG, "Error resolving streams: " + e.getMessage());
                return new StreamResult("Error: " + e.getMessage(), resolveTime);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> testProvider() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Test with a known working movie
                MediaInfo testMedia = new MediaInfo("550", "movie", "Fight Club", "1999");
                String embedUrl = buildEmbedUrl(testMedia);
                
                URL url = new URL(embedUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                connection.disconnect();
                
                boolean working = responseCode == 200 || responseCode == 302; // Accept redirects as working
                Log.d(TAG, "Provider test result: " + working + " (response: " + responseCode + ")");
                return working;
                
            } catch (Exception e) {
                Log.w(TAG, "Provider test failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }
    
    /**
     * Build embed URL for media
     */
    private String buildEmbedUrl(MediaInfo mediaInfo) {
        if (mediaInfo.isMovie()) {
            return BASE_URL + "/movie/" + mediaInfo.id;
        } else {
            // For TV shows, include season and episode if available
            if (mediaInfo.season != null && mediaInfo.episode != null) {
                return BASE_URL + "/tv/" + mediaInfo.id + "/" + mediaInfo.season + "/" + mediaInfo.episode;
            } else {
                return BASE_URL + "/tv/" + mediaInfo.id + "/1/1"; // Default to season 1, episode 1
            }
        }
    }
    
    /**
     * Extract stream URLs from embed page
     */
    private List<StreamSource> extractStreams(String embedUrl) throws Exception {
        List<StreamSource> sources = new ArrayList<>();
        
        // Create connection
        URL url = new URL(embedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        // Set headers to mimic browser
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", 
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        connection.setRequestProperty("DNT", "1");
        connection.setRequestProperty("Connection", "keep-alive");
        
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true); // Follow redirects
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("HTTP error: " + responseCode);
        }
        
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
        }
        reader.close();
        connection.disconnect();
        
        String html = response.toString();
        Log.d(TAG, "Received HTML content (" + html.length() + " chars)");
        
        // Extract streams using different patterns for this provider
        extractStreamSources(html, sources, embedUrl);
        
        return sources;
    }
    
    /**
     * Extract stream sources using provider-specific patterns
     */
    private void extractStreamSources(String html, List<StreamSource> sources, String referer) {
        // Pattern 1: Look for video sources in JavaScript
        Pattern sourcePattern = Pattern.compile("(?:source|src|url)[\"'\\s]*:[\"'\\s]*(https?://[^\"'\\s]+)(?:\\.m3u8|\\.mp4)", Pattern.CASE_INSENSITIVE);
        Matcher sourceMatcher = sourcePattern.matcher(html);
        
        while (sourceMatcher.find()) {
            String streamUrl = sourceMatcher.group(1);
            String type = streamUrl.contains(".m3u8") ? "hls" : "mp4";
            String quality = extractQuality(streamUrl, "auto");
            
            Bundle headers = createHeaders(streamUrl, referer);
            StreamSource source = new StreamSource(streamUrl, quality, type, headers, true);
            sources.add(source);
            
            Log.d(TAG, "Found " + type.toUpperCase() + " stream: " + streamUrl + " (quality: " + quality + ")");
        }
        
        // Pattern 2: Look for m3u8 URLs specifically
        Pattern hlsPattern = Pattern.compile("(https?://[^\\s\"']+\\.m3u8(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher hlsMatcher = hlsPattern.matcher(html);
        
        while (hlsMatcher.find()) {
            String streamUrl = hlsMatcher.group(1);
            String quality = extractQuality(streamUrl, "auto");
            
            Bundle headers = createHeaders(streamUrl, referer);
            StreamSource source = new StreamSource(streamUrl, quality, "hls", headers, true);
            sources.add(source);
            
            Log.d(TAG, "Found HLS stream: " + streamUrl + " (quality: " + quality + ")");
        }
        
        // Pattern 3: Look for mp4 URLs
        Pattern mp4Pattern = Pattern.compile("(https?://[^\\s\"']+\\.mp4(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher mp4Matcher = mp4Pattern.matcher(html);
        
        while (mp4Matcher.find()) {
            String streamUrl = mp4Matcher.group(1);
            String quality = extractQuality(streamUrl, "unknown");
            
            Bundle headers = createHeaders(streamUrl, referer);
            StreamSource source = new StreamSource(streamUrl, quality, "mp4", headers, true);
            sources.add(source);
            
            Log.d(TAG, "Found MP4 stream: " + streamUrl + " (quality: " + quality + ")");
        }
    }
    
    /**
     * Extract quality information from URL
     */
    private String extractQuality(String url, String defaultQuality) {
        // Check for quality indicators in URL
        if (url.contains("1080")) return "1080p";
        if (url.contains("720")) return "720p";
        if (url.contains("480")) return "480p";
        if (url.contains("360")) return "360p";
        if (url.contains("240")) return "240p";
        
        // Check for quality parameters
        if (url.contains("q=")) {
            Pattern qualityPattern = Pattern.compile("q=([^&]+)");
            Matcher matcher = qualityPattern.matcher(url);
            if (matcher.find()) {
                String q = matcher.group(1);
                return q.contains("p") ? q : q + "p";
            }
        }
        
        return defaultQuality;
    }
    
    /**
     * Create headers for stream request
     */
    private Bundle createHeaders(String streamUrl, String referer) {
        Bundle headers = new Bundle();
        
        try {
            URL url = new URL(streamUrl);
            String host = url.getHost();
            
            // Standard headers
            headers.putString("User-Agent", 
                "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
            headers.putString("Accept", "*/*");
            headers.putString("Accept-Language", "en-US,en;q=0.9");
            headers.putString("Accept-Encoding", "identity");
            
            // Set referer and origin
            if (host != null && (host.contains("embed.su") || host.contains("superstream"))) {
                headers.putString("Referer", "https://embed.su/");
                headers.putString("Origin", "https://embed.su");
            } else {
                headers.putString("Referer", referer);
                
                // Extract origin from referer
                try {
                    URL refererUrl = new URL(referer);
                    String origin = refererUrl.getProtocol() + "://" + refererUrl.getHost();
                    if (refererUrl.getPort() != -1 && refererUrl.getPort() != 80 && refererUrl.getPort() != 443) {
                        origin += ":" + refererUrl.getPort();
                    }
                    headers.putString("Origin", origin);
                } catch (Exception e) {
                    headers.putString("Origin", "https://embed.su");
                }
            }
            
            Log.d(TAG, "Created headers for host: " + host);
            
        } catch (Exception e) {
            Log.w(TAG, "Error creating headers: " + e.getMessage());
            
            // Fallback headers
            headers.putString("Referer", "https://embed.su/");
            headers.putString("Origin", "https://embed.su");
        }
        
        return headers;
    }
}
