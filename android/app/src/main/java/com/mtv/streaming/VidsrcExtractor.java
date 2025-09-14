package com.mtv.streaming;

import android.os.Bundle;
import android.util.Log;
import android.util.Base64;
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
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URLDecoder;

/**
 * VidSrc.to extractor based on the documentation from deepwiki.com
 * Implements proper server selection and RCP endpoint extraction
 */
public class VidsrcExtractor {
    private static final String TAG = "VidsrcExtractor";
    private static final String BASE_URL = "https://vidsrc.to/embed";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
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
    
    public interface StreamCallback {
        void onStreamResolved(StreamResult result);
    }
    
    /**
     * Main entry point - resolves VidSrc.to embed URL to direct stream
     */
    public static void resolveStream(String mediaId, String mediaType, String title, String year, StreamCallback callback) {
        CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "=== VidSrc.to Extraction Started ===");
                Log.d(TAG, "Media: " + title + " (" + mediaType + ")");
                Log.d(TAG, "TMDB ID: " + mediaId);
                Log.d(TAG, "Year: " + year);
                
                // Step 1: Build embed URL
                String embedUrl = buildEmbedUrl(mediaId, mediaType);
                Log.d(TAG, "Embed URL: " + embedUrl);
                
                // Step 2: Fetch embed page and extract servers
                String embedHtml = fetchEmbedPage(embedUrl);
                if (embedHtml == null) {
                    return new StreamResult("Failed to fetch embed page");
                }
                
                // Step 3: Extract server information
                List<ServerInfo> servers = extractServers(embedHtml);
                Log.d(TAG, "Found " + servers.size() + " servers");
                
                // If no servers found, try alternative extraction methods
                if (servers.isEmpty()) {
                    Log.w(TAG, "No servers found with primary method, trying alternatives...");
                    servers = tryAlternativeServerExtraction(embedHtml);
                    Log.d(TAG, "Alternative extraction found " + servers.size() + " servers");
                }
                
                if (servers.isEmpty()) {
                    return new StreamResult("No servers found in embed page. URL tested: " + embedUrl);
                }
                
                // Step 4: Try each server until we find a working stream
                for (ServerInfo server : servers) {
                    try {
                        Log.d(TAG, "Trying server: " + server.name + " (hash: " + server.dataHash + ")");
                        
                        StreamResult result = extractFromServer(server, embedUrl);
                        if (result != null && result.success) {
                            Log.d(TAG, "✓ Successfully extracted stream from server: " + server.name);
                            return result;
                        }
                        
                    } catch (Exception e) {
                        Log.w(TAG, "Server " + server.name + " failed: " + e.getMessage());
                    }
                }
                
                return new StreamResult("All servers failed to provide streams");
                
            } catch (Exception e) {
                Log.e(TAG, "Extraction failed: " + e.getMessage());
                e.printStackTrace();
                return new StreamResult("Extraction error: " + e.getMessage());
            }
        }, executor).thenAccept(callback::onStreamResolved);
    }
    
    /**
     * Build VidSrc.to embed URL
     */
    private static String buildEmbedUrl(String mediaId, String mediaType) {
        String embedUrl;
        if ("movie".equals(mediaType)) {
            embedUrl = BASE_URL + "/movie/" + mediaId;
        } else {
            // For TV shows, default to season 1, episode 1
            embedUrl = BASE_URL + "/tv/" + mediaId + "/1/1";
        }
        
        Log.d(TAG, "=== URL BUILDING DEBUG ===");
        Log.d(TAG, "Input Media ID: " + mediaId);
        Log.d(TAG, "Input Media Type: " + mediaType);
        Log.d(TAG, "Built Embed URL: " + embedUrl);
        Log.d(TAG, "Expected working URL format: https://vidsrc.to/embed/movie/18415");
        
        return embedUrl;
    }
    
    /**
     * Fetch embed page with proper headers
     */
    private static String fetchEmbedPage(String embedUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(embedUrl).openConnection();
            
            // Set headers to mimic real browser
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", 
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Sec-Fetch-Dest", "document");
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
            connection.setRequestProperty("Sec-Fetch-Site", "none");
            connection.setRequestProperty("Sec-Fetch-User", "?1");
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
            
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "=== HTTP RESPONSE INFO ===");
            Log.d(TAG, "Response Code: " + responseCode);
            Log.d(TAG, "Response Message: " + connection.getResponseMessage());
            Log.d(TAG, "Content Type: " + connection.getContentType());
            Log.d(TAG, "Content Length: " + connection.getContentLength());
            Log.d(TAG, "URL: " + embedUrl);
            
            if (responseCode != 200) {
                Log.e(TAG, "❌ Failed to fetch embed page: HTTP " + responseCode + " - " + connection.getResponseMessage());
                
                // Try to read error response
                try {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine).append("\n");
                    }
                    errorReader.close();
                    
                    if (errorResponse.length() > 0) {
                        String errorPreview = errorResponse.length() > 1000 ? errorResponse.substring(0, 1000) + "..." : errorResponse.toString();
                        Log.e(TAG, "Error response content: " + errorPreview);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not read error response: " + e.getMessage());
                }
                
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            connection.disconnect();
            
            String html = response.toString();
            Log.d(TAG, "Embed page HTML length: " + html.length());
            
            // Log the first 2000 characters of HTML content for debugging
            if (html.length() > 0) {
                String htmlPreview = html.length() > 2000 ? html.substring(0, 2000) + "..." : html;
                Log.d(TAG, "=== HTML CONTENT PREVIEW ===");
                Log.d(TAG, htmlPreview);
                Log.d(TAG, "=== END HTML PREVIEW ===");
                
                // Check for common blocking indicators
                if (html.toLowerCase().contains("cloudflare")) {
                    Log.w(TAG, "⚠️ Cloudflare protection detected in HTML");
                }
                if (html.toLowerCase().contains("captcha")) {
                    Log.w(TAG, "⚠️ CAPTCHA challenge detected in HTML");
                }
                if (html.toLowerCase().contains("blocked")) {
                    Log.w(TAG, "⚠️ Request appears to be blocked");
                }
                if (html.toLowerCase().contains("403") || html.toLowerCase().contains("forbidden")) {
                    Log.w(TAG, "⚠️ 403 Forbidden response detected");
                }
                if (html.length() < 500) {
                    Log.w(TAG, "⚠️ Suspiciously short HTML response (possible blocking)");
                }
            } else {
                Log.e(TAG, "❌ Empty HTML response received");
            }
            
            return html;
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching embed page: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract server information from embed page HTML
     */
    private static List<ServerInfo> extractServers(String html) {
        List<ServerInfo> servers = new ArrayList<>();
        
        try {
            Log.d(TAG, "=== SERVER EXTRACTION DEBUG ===");
            
            // Updated patterns based on actual VidSrc.to structure
            // Pattern 1: Standard server elements with data-hash
            Pattern serverPattern = Pattern.compile(
                "<[^>]*data-hash=[\"']([^\"']+)[\"'][^>]*>([^<]*)</[^>]*>", 
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            
            Matcher matcher = serverPattern.matcher(html);
            int matchCount = 0;
            
            while (matcher.find()) {
                matchCount++;
                String dataHash = matcher.group(1);
                String serverName = matcher.group(2);
                
                Log.d(TAG, "Match " + matchCount + " - Raw hash: '" + dataHash + "', Raw name: '" + serverName + "'");
                
                // Clean up server name
                if (serverName != null) {
                    serverName = serverName.replaceAll("<[^>]*>", "").trim();
                }
                
                if (dataHash != null && !dataHash.isEmpty() && serverName != null && !serverName.isEmpty()) {
                    ServerInfo server = new ServerInfo();
                    server.dataHash = dataHash;
                    server.name = serverName;
                    servers.add(server);
                    
                    Log.d(TAG, "Found server: " + serverName + " (hash: " + dataHash + ")");
                } else {
                    Log.w(TAG, "✗ Skipped invalid server - hash: '" + dataHash + "', name: '" + serverName + "'");
                }
            }
            
            Log.d(TAG, "Primary pattern found " + matchCount + " matches, " + servers.size() + " valid servers");
            
            // Pattern 2: More flexible server detection
            if (servers.isEmpty()) {
                Log.d(TAG, "Trying flexible server pattern...");
                Pattern flexPattern = Pattern.compile(
                    "data-hash=[\"']([^\"']+)[\"']", 
                    Pattern.CASE_INSENSITIVE
                );
                
                Matcher flexMatcher = flexPattern.matcher(html);
                int altMatchCount = 0;
                while (flexMatcher.find() && altMatchCount < 5) { // Limit to 5 servers
                    altMatchCount++;
                    String dataHash = flexMatcher.group(1);
                    String serverName = "Server " + altMatchCount;
                    
                    Log.d(TAG, "Alt Match " + altMatchCount + " - hash: '" + dataHash + "', name: '" + serverName + "'");
                    
                    if (!dataHash.isEmpty() && !serverName.isEmpty()) {
                        ServerInfo server = new ServerInfo();
                        server.dataHash = dataHash;
                        server.name = serverName;
                        servers.add(server);
                        
                        Log.d(TAG, "Found server (flex pattern): " + serverName + " (hash: " + dataHash + ")");
                    }
                }
                Log.d(TAG, "Flexible pattern found " + altMatchCount + " matches");
            }
            
            // Pattern 3: Look for iframe sources as fallback
            if (servers.isEmpty()) {
                Log.d(TAG, "Trying iframe source extraction...");
                Pattern iframePattern = Pattern.compile(
                    "<iframe[^>]*src=[\"']([^\"']+)[\"'][^>]*>", 
                    Pattern.CASE_INSENSITIVE
                );
                
                Matcher iframeMatcher = iframePattern.matcher(html);
                int iframeCount = 0;
                while (iframeMatcher.find() && iframeCount < 3) {
                    iframeCount++;
                    String iframeSrc = iframeMatcher.group(1);
                    
                    // Extract hash-like parameter from iframe src
                    if (iframeSrc.contains("=")) {
                        String[] parts = iframeSrc.split("=");
                        if (parts.length > 1) {
                            String possibleHash = parts[parts.length - 1];
                            if (possibleHash.length() > 10) { // Reasonable hash length
                                ServerInfo server = new ServerInfo();
                                server.dataHash = possibleHash;
                                server.name = "Iframe Server " + iframeCount;
                                servers.add(server);
                                
                                Log.d(TAG, "Found iframe server: " + server.name + " (hash: " + possibleHash + ")");
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting servers: " + e.getMessage());
        }
        
        Log.d(TAG, "=== FINAL SERVER COUNT: " + servers.size() + " ===");
        return servers;
    }
    
    /**
     * Try alternative patterns for server extraction if main method fails
     */
    private static List<ServerInfo> tryAlternativeServerExtraction(String html) {
        List<ServerInfo> servers = new ArrayList<>();
        
        Log.d(TAG, "--- Trying Alternative Server Patterns ---");
        
        try {
            // Pattern 1: Look for any element with data-hash attribute (more flexible)
            Pattern pattern1 = Pattern.compile("data-hash=[\"']([^\"']{10,})[\"']", Pattern.CASE_INSENSITIVE);
            Matcher matcher1 = pattern1.matcher(html);
            
            int count = 0;
            while (matcher1.find() && count < 5) { // Limit to prevent spam
                String dataHash = matcher1.group(1);
                if (dataHash != null && !dataHash.isEmpty() && dataHash.length() > 10) {
                    ServerInfo server = new ServerInfo();
                    server.dataHash = dataHash;
                    server.name = "Server " + (count + 1); // Generic name
                    servers.add(server);
                    Log.d(TAG, "Alt Pattern 1 - Found hash: " + dataHash);
                    count++;
                }
            }
            
            // Pattern 2: Look for onclick or data attributes that might contain hashes
            Pattern pattern2 = Pattern.compile("(?:onclick|data-[a-z]+)=[\"'][^\"']*([a-zA-Z0-9]{20,})[^\"']*[\"']", Pattern.CASE_INSENSITIVE);
            Matcher matcher2 = pattern2.matcher(html);
            
            int count2 = 0;
            while (matcher2.find() && count2 < 3) {
                String dataHash = matcher2.group(1);
                if (dataHash != null && !dataHash.isEmpty() && dataHash.length() > 15) {
                    // Check if we already have this hash
                    boolean exists = servers.stream().anyMatch(s -> s.dataHash.equals(dataHash));
                    if (!exists) {
                        ServerInfo server = new ServerInfo();
                        server.dataHash = dataHash;
                        server.name = "Server (onclick pattern)";
                        servers.add(server);
                        Log.d(TAG, "Alt Pattern 2 - Found hash: " + dataHash);
                        count2++;
                    }
                }
            }
            
            // Pattern 3: Look for any long alphanumeric strings that could be hashes
            Pattern pattern3 = Pattern.compile("([a-zA-Z0-9]{25,})", Pattern.CASE_INSENSITIVE);
            Matcher matcher3 = pattern3.matcher(html);
            
            int count3 = 0;
            while (matcher3.find() && count3 < 2) {
                String dataHash = matcher3.group(1);
                
                if (dataHash != null && dataHash.length() > 20 && dataHash.length() < 100) {
                    // Skip common non-hash strings
                    if (!dataHash.toLowerCase().contains("mozilla") && 
                        !dataHash.toLowerCase().contains("webkit") && 
                        !dataHash.toLowerCase().contains("chrome") &&
                        !dataHash.toLowerCase().contains("script")) {
                        
                        // Check if we already have this hash
                        boolean exists = servers.stream().anyMatch(s -> s.dataHash.equals(dataHash));
                        if (!exists) {
                            ServerInfo server = new ServerInfo();
                            server.dataHash = dataHash;
                            server.name = "Server (hash pattern)";
                            servers.add(server);
                            Log.d(TAG, "Alt Pattern 3 - Found potential hash: " + dataHash);
                            count3++;
                        }
                    }
                }
            }
            
            // Pattern 4: Look for script tags that might contain server data
            Pattern pattern4 = Pattern.compile("<script[^>]*>([^<]*(?:server|hash|source)[^<]*)</script>", Pattern.CASE_INSENSITIVE);
            Matcher matcher4 = pattern4.matcher(html);
            
            while (matcher4.find()) {
                String scriptContent = matcher4.group(1);
                Log.d(TAG, "Found script with server/hash content: " + scriptContent.substring(0, Math.min(200, scriptContent.length())));
                
                // Look for hash-like strings in script content
                Pattern hashInScript = Pattern.compile("([a-zA-Z0-9]{20,40})", Pattern.CASE_INSENSITIVE);
                Matcher hashMatcher = hashInScript.matcher(scriptContent);
                
                int scriptHashCount = 0;
                while (hashMatcher.find() && scriptHashCount < 2) {
                    String possibleHash = hashMatcher.group(1);
                    boolean exists = servers.stream().anyMatch(s -> s.dataHash.equals(possibleHash));
                    if (!exists) {
                        ServerInfo server = new ServerInfo();
                        server.dataHash = possibleHash;
                        server.name = "Server (script)";
                        servers.add(server);
                        Log.d(TAG, "Alt Pattern 4 - Found script hash: " + possibleHash);
                        scriptHashCount++;
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in alternative server extraction: " + e.getMessage());
        }
        
        Log.d(TAG, "Alternative extraction found " + servers.size() + " total servers");
        return servers;
    }
    
    /**
     * Extract stream from server using RCP endpoint
     */
    private static StreamResult extractFromServer(ServerInfo server, String referer) {
        try {
            // Step 1: Build RCP URL
            String rcpUrl = "https://vidsrc.to/rcp/" + server.dataHash;
            Log.d(TAG, "RCP URL: " + rcpUrl);
            
            // Step 2: Fetch RCP response
            String rcpResponse = fetchRcpResponse(rcpUrl, referer);
            if (rcpResponse == null) {
                Log.w(TAG, "Failed to fetch RCP response");
                return null;
            }
            
            Log.d(TAG, "RCP response length: " + rcpResponse.length());
            Log.d(TAG, "RCP response preview: " + rcpResponse.substring(0, Math.min(200, rcpResponse.length())));
            
            // Step 3: Extract stream URL from RCP response
            String streamUrl = extractStreamFromRcp(rcpResponse);
            if (streamUrl == null) {
                Log.w(TAG, "No stream URL found in RCP response");
                return null;
            }
            
            Log.d(TAG, "Extracted stream URL: " + streamUrl);
            
            // Step 4: Determine MIME type and create headers
            String mimeType = streamUrl.contains(".m3u8") ? "application/x-mpegURL" : "video/mp4";
            Bundle headers = createStreamHeaders(streamUrl, referer);
            
            return new StreamResult(streamUrl, mimeType, headers);
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting from server: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Fetch RCP response with proper headers
     */
    private static String fetchRcpResponse(String rcpUrl, String referer) {
        try {
            Log.d(TAG, "=== FETCHING RCP RESPONSE ===");
            Log.d(TAG, "RCP URL: " + rcpUrl);
            Log.d(TAG, "Referer: " + referer);
            
            HttpURLConnection connection = (HttpURLConnection) new URL(rcpUrl).openConnection();
            
            // Set headers for RCP request
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Referer", referer);
            connection.setRequestProperty("Origin", "https://vidsrc.to");
            connection.setRequestProperty("Sec-Fetch-Dest", "empty");
            connection.setRequestProperty("Sec-Fetch-Mode", "cors");
            connection.setRequestProperty("Sec-Fetch-Site", "same-origin");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "RCP HTTP response code: " + responseCode);
            Log.d(TAG, "RCP response message: " + connection.getResponseMessage());
            Log.d(TAG, "RCP content type: " + connection.getContentType());
            
            if (responseCode != 200 && responseCode != 302) {
                Log.w(TAG, "RCP request failed: HTTP " + responseCode + " - " + connection.getResponseMessage());
                
                // Try to read error response
                try {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine).append("\n");
                    }
                    errorReader.close();
                    
                    if (errorResponse.length() > 0) {
                        String errorPreview = errorResponse.length() > 500 ? errorResponse.substring(0, 500) + "..." : errorResponse.toString();
                        Log.w(TAG, "RCP error response: " + errorPreview);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not read RCP error response: " + e.getMessage());
                }
                
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            connection.disconnect();
            
            String responseText = response.toString();
            Log.d(TAG, "✓ RCP response received, length: " + responseText.length());
            return responseText;
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching RCP response: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Extract stream URL from RCP response
     */
    private static String extractStreamFromRcp(String rcpResponse) {
        try {
            Log.d(TAG, "=== RCP RESPONSE ANALYSIS ===");
            Log.d(TAG, "Response length: " + rcpResponse.length());
            Log.d(TAG, "Response preview: " + rcpResponse.substring(0, Math.min(500, rcpResponse.length())));
            
            // Method 1: Try to parse as JSON
            try {
                JSONObject json = new JSONObject(rcpResponse);
                Log.d(TAG, "Successfully parsed as JSON");
                
                // Check common JSON keys for stream URLs
                String[] keys = {"url", "file", "source", "stream", "src"};
                for (String key : keys) {
                    if (json.has(key)) {
                        String url = json.getString(key);
                        if (url != null && !url.isEmpty()) {
                            Log.d(TAG, "Found stream URL in JSON key '" + key + "': " + url);
                            
                            // Validate URL format
                            if (url.startsWith("http") && (url.contains(".m3u8") || url.contains(".mp4") || url.contains("stream"))) {
                                return url;
                            } else if (url.startsWith("http")) {
                                // Even if it doesn't have obvious video extension, try it
                                Log.d(TAG, "Found HTTP URL without video extension, trying anyway: " + url);
                                return url;
                            }
                        }
                    }
                }
                
                // Check for nested objects
                if (json.has("result") && json.get("result") instanceof JSONObject) {
                    JSONObject result = json.getJSONObject("result");
                    Log.d(TAG, "Checking nested result object");
                    for (String key : keys) {
                        if (result.has(key)) {
                            String url = result.getString(key);
                            if (url != null && !url.isEmpty() && url.startsWith("http")) {
                                Log.d(TAG, "Found stream URL in result." + key + ": " + url);
                                return url;
                            }
                        }
                    }
                }
                
                // Check for sources array
                if (json.has("sources") && json.get("sources") instanceof JSONArray) {
                    JSONArray sources = json.getJSONArray("sources");
                    Log.d(TAG, "Found sources array with " + sources.length() + " items");
                    for (int i = 0; i < sources.length(); i++) {
                        JSONObject source = sources.getJSONObject(i);
                        for (String key : keys) {
                            if (source.has(key)) {
                                String url = source.getString(key);
                                if (url != null && !url.isEmpty() && url.startsWith("http")) {
                                    Log.d(TAG, "Found stream URL in sources[" + i + "]." + key + ": " + url);
                                    return url;
                                }
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                Log.d(TAG, "RCP response is not JSON: " + e.getMessage() + ", trying other methods");
            }
            
            // Method 2: Direct URL pattern matching (most reliable)
            Log.d(TAG, "Trying direct URL pattern matching...");
            Pattern directPattern = Pattern.compile("(https?://[^\\s\"'<>]+(?:\\.m3u8|\\.mp4|/stream|/play)(?:\\?[^\\s\"'<>]*)?)", Pattern.CASE_INSENSITIVE);
            Matcher directMatcher = directPattern.matcher(rcpResponse);
            
            if (directMatcher.find()) {
                String url = directMatcher.group(1);
                Log.d(TAG, "Found direct stream URL: " + url);
                return url;
            }
            
            // Method 3: Look for any HTTP URL as fallback
            Log.d(TAG, "Trying any HTTP URL pattern...");
            Pattern httpPattern = Pattern.compile("(https?://[^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);
            Matcher httpMatcher = httpPattern.matcher(rcpResponse);
            
            while (httpMatcher.find()) {
                String url = httpMatcher.group(1);
                // Skip common non-video URLs
                if (!url.contains("google") && !url.contains("facebook") && !url.contains("twitter") && 
                    !url.contains("ads") && !url.contains("analytics") && url.length() > 20) {
                    Log.d(TAG, "Found potential stream URL: " + url);
                    return url;
                }
            }
            
            // Method 4: Look for encoded data that might need decoding
            Log.d(TAG, "Trying base64 decoding...");
            Pattern encodedPattern = Pattern.compile("(?:data|source|url)\\s*[:=]\\s*[\"']([A-Za-z0-9+/=]+)[\"']");
            Matcher encodedMatcher = encodedPattern.matcher(rcpResponse);
            
            while (encodedMatcher.find()) {
                String encoded = encodedMatcher.group(1);
                try {
                    // Try base64 decoding
                    byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
                    String decodedStr = new String(decoded);
                    Log.d(TAG, "Decoded string: " + decodedStr);
                    
                    if (decodedStr.contains("http")) {
                        // Look for URLs in decoded content
                        Pattern urlPattern = Pattern.compile("(https?://[^\\s\"']+(?:\\.m3u8|\\.mp4)(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
                        Matcher urlMatcher = urlPattern.matcher(decodedStr);
                        
                        if (urlMatcher.find()) {
                            String url = urlMatcher.group(1);
                            Log.d(TAG, "Found stream URL in decoded data: " + url);
                            return url;
                        }
                    }
                } catch (Exception e) {
                    // Not base64 or invalid, continue
                    Log.d(TAG, "Failed to decode: " + e.getMessage());
                }
            }
            
            // Method 5: Look for src: pattern specifically
            Log.d(TAG, "Trying src pattern matching...");
            Pattern srcPattern = Pattern.compile("src\\s*[:=]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher srcMatcher = srcPattern.matcher(rcpResponse);
            
            if (srcMatcher.find()) {
                String srcData = srcMatcher.group(1);
                Log.d(TAG, "Found src data: " + srcData);
                
                // Check if it's a direct URL
                if (srcData.startsWith("http")) {
                    return srcData;
                }
                
                // Try to decode if it looks encoded
                try {
                    byte[] decoded = Base64.decode(srcData, Base64.DEFAULT);
                    String decodedStr = new String(decoded);
                    
                    Pattern urlPattern = Pattern.compile("(https?://[^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);
                    Matcher urlMatcher = urlPattern.matcher(decodedStr);
                    
                    if (urlMatcher.find()) {
                        String url = urlMatcher.group(1);
                        Log.d(TAG, "Found stream URL in decoded src: " + url);
                        return url;
                    }
                } catch (Exception e) {
                    // Not base64, continue
                }
            }
            
            Log.w(TAG, "✗ No stream URL found in RCP response after trying all methods");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting stream from RCP: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create headers for stream playback
     */
    private static Bundle createStreamHeaders(String streamUrl, String referer) {
        Bundle headers = new Bundle();
        
        try {
            URL url = new URL(streamUrl);
            String host = url.getHost();
            
            // Standard headers for video streaming
            headers.putString("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
            headers.putString("Accept", "*/*");
            headers.putString("Accept-Encoding", "identity");
            headers.putString("Accept-Language", "en-US,en;q=0.9");
            
            // Set referer based on the stream host
            if (host != null && host.contains("vidsrc")) {
                headers.putString("Referer", "https://vidsrc.to/");
                headers.putString("Origin", "https://vidsrc.to");
            } else {
                headers.putString("Referer", referer);
            }
            
            Log.d(TAG, "Created headers for stream host: " + host);
            
        } catch (Exception e) {
            Log.w(TAG, "Error creating headers: " + e.getMessage());
            // Fallback headers
            headers.putString("Referer", "https://vidsrc.to/");
            headers.putString("Origin", "https://vidsrc.to");
        }
        
        return headers;
    }
    
    /**
     * Server information class
     */
    private static class ServerInfo {
        String name;
        String dataHash;
    }
}