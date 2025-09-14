package com.mtv.streaming;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.HashMap;
import java.util.Map;

/**
 * Stream Extraction Pipeline for VidSRC embed links
 * Converts embed URLs to direct playable streams (.m3u8 or .mp4)
 */
public class StreamExtractor {
    private static final String TAG = "StreamExtractor";
    
    // Standard headers for all requests
    private static final Map<String, String> STANDARD_HEADERS = new HashMap<String, String>() {{
        put("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
        put("Referer", "https://vidsrc.net/");
        put("Accept", "text/html,application/json,*/*");
        put("Origin", "https://vidsrc.net");
        put("Accept-Language", "en-US,en;q=0.9");
        put("Cache-Control", "no-cache");
        put("DNT", "1");
        put("Connection", "keep-alive");
    }};
    
    /**
     * Stream extraction result
     */
    public static class StreamResult {
        public final String url;
        public final String mimeType;
        public final Bundle headers;
        public final boolean success;
        public final String error;
        
        public StreamResult(String url, String mimeType, Bundle headers) {
            this.url = url;
            this.mimeType = mimeType;
            this.headers = headers;
            this.success = true;
            this.error = null;
        }
        
        public StreamResult(String error) {
            this.url = null;
            this.mimeType = null;
            this.headers = null;
            this.success = false;
            this.error = error;
        }
    }
    
    /**
     * Callback interface for async stream resolution
     */
    public interface StreamCallback {
        void onStreamResolved(StreamResult result);
    }
    
    /**
     * Main entry point - resolves embed URL to direct stream
     */
    public static void resolveStream(String embedUrl, StreamCallback callback) {
        new AsyncTask<String, Void, StreamResult>() {
            @Override
            protected StreamResult doInBackground(String... urls) {
                String embedUrl = urls[0];
                Log.d(TAG, "=== Stream Extraction Pipeline Started ===");
                Log.d(TAG, "Input embed URL: " + embedUrl);
                
                try {
                    // Method 1: API extraction (highest priority)
                    StreamResult result = tryVidsrcNetExtraction(embedUrl);
                    if (result != null && result.success) {
                        Log.d(TAG, "✓ API extraction successful");
                        return result;
                    }
                    
                    // Method 2: HTML scrape (vidsrc.to)
                    result = tryVidsrcToExtraction(embedUrl);
                    if (result != null && result.success) {
                        Log.d(TAG, "✓ VidsrcTo extraction successful");
                        return result;
                    }
                    
                    // Method 3: HTML scrape (vidsrc.net fallback)
                    result = tryGenericExtraction(embedUrl);
                    if (result != null && result.success) {
                        Log.d(TAG, "✓ Generic extraction successful");
                        return result;
                    }
                    
                    Log.e(TAG, "✗ All extraction methods failed");
                    return new StreamResult("All extraction methods failed - no playable stream found");
                    
                } catch (Exception e) {
                    Log.e(TAG, "✗ Stream extraction error: " + e.getMessage());
                    return new StreamResult("Extraction error: " + e.getMessage());
                }
            }
            
            @Override
            protected void onPostExecute(StreamResult result) {
                if (result.success) {
                    Log.d(TAG, "=== Extraction Complete - SUCCESS ===");
                    Log.d(TAG, "Stream URL: " + result.url);
                    Log.d(TAG, "MIME Type: " + result.mimeType);
                } else {
                    Log.d(TAG, "=== Extraction Complete - FAILED ===");
                    Log.d(TAG, "Error: " + result.error);
                }
                callback.onStreamResolved(result);
            }
        }.execute(embedUrl);
    }
    
    /**
     * Method 1: API extraction from vidsrc.net API
     */
    private static StreamResult tryVidsrcNetExtraction(String embedUrl) {
        try {
            Log.d(TAG, "--- Method 1: API Extraction ---");
            
            // Convert embed URL to API URL
            String apiUrl = convertToApiUrl(embedUrl);
            if (apiUrl == null) {
                Log.w(TAG, "Could not convert to API URL");
                return null;
            }
            
            Log.d(TAG, "API URL: " + apiUrl);
            
            // Fetch JSON response
            Map<String, String> apiHeaders = new HashMap<>(STANDARD_HEADERS);
            apiHeaders.put("Accept", "application/json");
            
            String jsonResponse = fetchUrl(apiUrl, apiHeaders);
            if (jsonResponse == null) {
                Log.w(TAG, "API request failed");
                return null;
            }
            
            Log.d(TAG, "API response length: " + jsonResponse.length());
            
            // Parse JSON for stream URL
            String streamUrl = parseJsonResponse(jsonResponse);
            if (streamUrl != null) {
                String mimeType = determineMimeType(streamUrl);
                Bundle headers = createStreamHeaders(streamUrl);
                
                Log.d(TAG, "✓ API extraction found stream: " + streamUrl);
                return new StreamResult(streamUrl, mimeType, headers);
            }
            
            Log.w(TAG, "No stream URL found in API response");
            return null;
            
        } catch (Exception e) {
            Log.w(TAG, "API extraction failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Method 2: HTML scraping from vidsrc.to
     */
    private static StreamResult tryVidsrcToExtraction(String embedUrl) {
        try {
            Log.d(TAG, "--- Method 2: VidsrcTo HTML Extraction ---");
            
            // Convert to vidsrc.to format
            String vidsrcToUrl = convertToVidsrcToUrl(embedUrl);
            if (vidsrcToUrl == null) {
                Log.w(TAG, "Could not convert to vidsrc.to URL");
                return null;
            }
            
            Log.d(TAG, "VidsrcTo URL: " + vidsrcToUrl);
            
            // Fetch HTML content
            String html = fetchUrl(vidsrcToUrl, STANDARD_HEADERS);
            if (html == null) {
                Log.w(TAG, "Failed to fetch vidsrc.to HTML");
                return null;
            }
            
            Log.d(TAG, "HTML content length: " + html.length());
            
            // Extract stream from HTML
            return extractFromHtml(html, vidsrcToUrl);
            
        } catch (Exception e) {
            Log.w(TAG, "VidsrcTo extraction failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Method 3: Generic HTML scraping from original embed URL
     */
    private static StreamResult tryGenericExtraction(String embedUrl) {
        try {
            Log.d(TAG, "--- Method 3: Generic HTML Extraction ---");
            Log.d(TAG, "Fetching original embed URL: " + embedUrl);
            
            // Fetch HTML content from original embed URL
            String html = fetchUrl(embedUrl, STANDARD_HEADERS);
            if (html == null) {
                Log.w(TAG, "Failed to fetch original embed HTML");
                return null;
            }
            
            Log.d(TAG, "HTML content length: " + html.length());
            
            // Extract stream from HTML
            return extractFromHtml(html, embedUrl);
            
        } catch (Exception e) {
            Log.w(TAG, "Generic extraction failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Core HTML extraction logic using regex patterns
     */
    private static StreamResult extractFromHtml(String html, String refererUrl) {
        Log.d(TAG, "Extracting streams from HTML content...");
        
        // Pattern 1: Direct .m3u8 links
        Pattern m3u8Pattern = Pattern.compile(
            "https?://[^\\s\"'<>]+\\.m3u8(?:\\?[^\\s\"'<>]*)?", 
            Pattern.CASE_INSENSITIVE
        );
        Matcher m3u8Matcher = m3u8Pattern.matcher(html);
        
        if (m3u8Matcher.find()) {
            String streamUrl = m3u8Matcher.group();
            Log.d(TAG, "✓ Found direct M3U8 link: " + streamUrl);
            Bundle headers = createStreamHeaders(streamUrl);
            return new StreamResult(streamUrl, "application/x-mpegURL", headers);
        }
        
        // Pattern 2: Direct .mp4 links
        Pattern mp4Pattern = Pattern.compile(
            "https?://[^\\s\"'<>]+\\.mp4(?:\\?[^\\s\"'<>]*)?", 
            Pattern.CASE_INSENSITIVE
        );
        Matcher mp4Matcher = mp4Pattern.matcher(html);
        
        if (mp4Matcher.find()) {
            String streamUrl = mp4Matcher.group();
            Log.d(TAG, "✓ Found direct MP4 link: " + streamUrl);
            Bundle headers = createStreamHeaders(streamUrl);
            return new StreamResult(streamUrl, "video/mp4", headers);
        }
        
        // Pattern 3: JavaScript assignments (source, src, url, file)
        Pattern jsPattern = Pattern.compile(
            "(?:source|src|url|file)\\s*[:=]\\s*[\"']([^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']", 
            Pattern.CASE_INSENSITIVE
        );
        Matcher jsMatcher = jsPattern.matcher(html);
        
        if (jsMatcher.find()) {
            String streamUrl = jsMatcher.group(1);
            
            // Ensure it's a complete URL
            if (!streamUrl.startsWith("http")) {
                Log.w(TAG, "Found relative URL, skipping: " + streamUrl);
            } else {
                Log.d(TAG, "✓ Found JS assignment stream: " + streamUrl);
                String mimeType = determineMimeType(streamUrl);
                Bundle headers = createStreamHeaders(streamUrl);
                return new StreamResult(streamUrl, mimeType, headers);
            }
        }
        
        Log.w(TAG, "No stream URLs found in HTML content");
        return null;
    }
    
    /**
     * Convert embed URL to API URL format
     */
    private static String convertToApiUrl(String embedUrl) {
        try {
            if (embedUrl.contains("vidsrc.net/embed/movie?tmdb=")) {
                String tmdbId = extractTmdbId(embedUrl);
                if (tmdbId != null) {
                    return "https://vidsrc.net/api/source/" + tmdbId;
                }
            } else if (embedUrl.contains("vidsrc.net/embed/tv?tmdb=")) {
                String tmdbId = extractTmdbId(embedUrl);
                if (tmdbId != null) {
                    return "https://vidsrc.net/api/source/" + tmdbId;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error converting to API URL: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Convert embed URL to vidsrc.to format
     */
    private static String convertToVidsrcToUrl(String embedUrl) {
        try {
            if (embedUrl.contains("vidsrc.net/embed/movie?tmdb=")) {
                String tmdbId = extractTmdbId(embedUrl);
                if (tmdbId != null) {
                    return "https://vidsrc.to/embed/movie/" + tmdbId;
                }
            } else if (embedUrl.contains("vidsrc.net/embed/tv?tmdb=")) {
                String tmdbId = extractTmdbId(embedUrl);
                String season = extractUrlParameter(embedUrl, "season");
                String episode = extractUrlParameter(embedUrl, "episode");
                
                if (tmdbId != null && season != null && episode != null) {
                    return "https://vidsrc.to/embed/tv/" + tmdbId + "/" + season + "/" + episode;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error converting to vidsrc.to URL: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract TMDB ID from embed URL
     */
    private static String extractTmdbId(String embedUrl) {
        try {
            Pattern pattern = Pattern.compile("tmdb=([^&]+)");
            Matcher matcher = pattern.matcher(embedUrl);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting TMDB ID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract URL parameter value
     */
    private static String extractUrlParameter(String url, String paramName) {
        try {
            Pattern pattern = Pattern.compile(paramName + "=([^&]+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting parameter " + paramName + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Parse JSON response for stream URL
     */
    private static String parseJsonResponse(String jsonResponse) {
        try {
            // Simple JSON parsing for common patterns
            String[] keys = {"url", "file", "source", "stream"};
            
            for (String key : keys) {
                Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(jsonResponse);
                if (matcher.find()) {
                    String url = matcher.group(1);
                    if (url.contains(".m3u8") || url.contains(".mp4")) {
                        Log.d(TAG, "Found stream URL in JSON key '" + key + "': " + url);
                        return url;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing JSON response: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Determine MIME type from URL
     */
    private static String determineMimeType(String url) {
        if (url.contains(".m3u8")) {
            return "application/x-mpegURL";
        } else if (url.contains(".mp4")) {
            return "video/mp4";
        } else {
            return "video/*";
        }
    }
    
    /**
     * Create headers bundle for stream playback
     */
    private static Bundle createStreamHeaders(String streamUrl) {
        Bundle headers = new Bundle();
        
        // Add all standard headers
        for (Map.Entry<String, String> entry : STANDARD_HEADERS.entrySet()) {
            headers.putString(entry.getKey(), entry.getValue());
        }
        
        // Add stream-specific headers
        try {
            URL url = new URL(streamUrl);
            String host = url.getHost();
            
            if (host != null) {
                if (host.contains("vidsrc")) {
                    headers.putString("Referer", "https://vidsrc.net/");
                    headers.putString("Origin", "https://vidsrc.net");
                } else {
                    headers.putString("Referer", "https://vidsrc.net/");
                }
            }
            
            Log.d(TAG, "Created headers for stream host: " + host);
            
        } catch (Exception e) {
            Log.w(TAG, "Error creating stream headers: " + e.getMessage());
        }
        
        return headers;
    }
    
    /**
     * Fetch URL content with headers
     */
    private static String fetchUrl(String urlString, Map<String, String> headers) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Set request method and timeouts
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            
            // Add headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP " + responseCode + " for " + urlString);
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                
                reader.close();
                connection.disconnect();
                
                return response.toString();
            } else {
                Log.w(TAG, "HTTP error " + responseCode + " for " + urlString);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch " + urlString + ": " + e.getMessage());
        }
        
        return null;
    }
}