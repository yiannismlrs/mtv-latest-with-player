package com.mtv.streaming.extractors;

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
 * VidsrcNet extractor using proper API endpoints
 * Based on vidsrc.net API documentation
 */
public class VidsrcNetExtractor extends StreamExtractor {
    private static final String TAG = "VidsrcNetExtractor";
    private static final String API_BASE_URL = "https://vidsrc.net/api";
    private static final String EMBED_BASE_URL = "https://vidsrc.net/embed";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    public String getName() {
        return "VidsrcNet";
    }
    
    @Override
    public CompletableFuture<ExtractionResult> extractStreams(MediaInfo mediaInfo) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                Log.d(TAG, "=== VidsrcNet API Extraction Started ===");
                Log.d(TAG, "Media: " + mediaInfo.title + " (" + mediaInfo.type + ")");
                Log.d(TAG, "TMDB ID: " + mediaInfo.id);
                
                List<ExtractedStream> streams = new ArrayList<>();
                
                // Method 1: Try API endpoints first
                try {
                    List<ExtractedStream> apiStreams = extractUsingAPI(mediaInfo);
                    streams.addAll(apiStreams);
                    Log.d(TAG, "API method found " + apiStreams.size() + " streams");
                } catch (Exception e) {
                    Log.w(TAG, "API method failed: " + e.getMessage());
                }
                
                // Method 2: Try embed page scraping as fallback
                if (streams.isEmpty()) {
                    try {
                        List<ExtractedStream> embedStreams = extractUsingEmbed(mediaInfo);
                        streams.addAll(embedStreams);
                        Log.d(TAG, "Embed method found " + embedStreams.size() + " streams");
                    } catch (Exception e) {
                        Log.w(TAG, "Embed method failed: " + e.getMessage());
                    }
                }
                
                // Method 3: Try direct source extraction
                if (streams.isEmpty()) {
                    try {
                        List<ExtractedStream> directStreams = extractDirectSources(mediaInfo);
                        streams.addAll(directStreams);
                        Log.d(TAG, "Direct method found " + directStreams.size() + " streams");
                    } catch (Exception e) {
                        Log.w(TAG, "Direct method failed: " + e.getMessage());
                    }
                }
                
                long extractionTime = System.currentTimeMillis() - startTime;
                
                if (!streams.isEmpty()) {
                    Log.d(TAG, "✓ Total streams extracted: " + streams.size());
                    for (ExtractedStream stream : streams) {
                        Log.d(TAG, "Stream: " + stream.quality + " " + stream.type + " - " + stream.url);
                    }
                    return new ExtractionResult(streams, extractionTime);
                } else {
                    Log.e(TAG, "✗ No streams found after trying all methods");
                    return new ExtractionResult("No streams found after trying all extraction methods", extractionTime);
                }
                
            } catch (Exception e) {
                long extractionTime = System.currentTimeMillis() - startTime;
                Log.e(TAG, "✗ Extraction failed: " + e.getMessage());
                e.printStackTrace();
                return new ExtractionResult("Extraction error: " + e.getMessage(), extractionTime);
            }
        }, executor);
    }
    
    /**
     * Method 1: Extract using vidsrc.net API endpoints
     */
    private List<ExtractedStream> extractUsingAPI(MediaInfo mediaInfo) throws Exception {
        List<ExtractedStream> streams = new ArrayList<>();
        
        Log.d(TAG, "--- Method 1: API Extraction ---");
        
        // Try different API endpoints based on the documentation
        String[] apiEndpoints = {
            API_BASE_URL + "/source/" + mediaInfo.id,
            API_BASE_URL + "/sources/" + mediaInfo.id,
            API_BASE_URL + "/stream/" + mediaInfo.id
        };
        
        for (String endpoint : apiEndpoints) {
            try {
                String apiUrl = endpoint;
                if (!mediaInfo.isMovie() && mediaInfo.season != null && mediaInfo.episode != null) {
                    apiUrl += "?s=" + mediaInfo.season + "&e=" + mediaInfo.episode;
                }
                
                Log.d(TAG, "Trying API endpoint: " + apiUrl);
                
                String response = fetchUrl(apiUrl, true);
                Log.d(TAG, "API response length: " + response.length());
                
                // Try to parse as JSON
                try {
                    JSONObject json = new JSONObject(response);
                    List<ExtractedStream> apiStreams = parseAPIResponse(json);
                    streams.addAll(apiStreams);
                    
                    if (!apiStreams.isEmpty()) {
                        Log.d(TAG, "✓ API endpoint successful: " + endpoint);
                        break; // Found streams, no need to try other endpoints
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse JSON from " + endpoint + ": " + e.getMessage());
                }
                
            } catch (Exception e) {
                Log.w(TAG, "API endpoint failed " + endpoint + ": " + e.getMessage());
            }
        }
        
        return streams;
    }
    
    /**
     * Method 2: Extract using embed page scraping
     */
    private List<ExtractedStream> extractUsingEmbed(MediaInfo mediaInfo) throws Exception {
        List<ExtractedStream> streams = new ArrayList<>();
        
        Log.d(TAG, "--- Method 2: Embed Extraction ---");
        
        String embedUrl = buildEmbedUrl(mediaInfo);
        Log.d(TAG, "Embed URL: " + embedUrl);
        
        String embedContent = fetchUrl(embedUrl, false);
        Log.d(TAG, "Embed content length: " + embedContent.length());
        
        // Extract servers from embed page
        List<ServerInfo> servers = extractServersFromEmbed(embedContent);
        Log.d(TAG, "Found " + servers.size() + " servers in embed page");
        
        // Process each server
        for (ServerInfo server : servers) {
            try {
                List<ExtractedStream> serverStreams = processServer(server, embedUrl);
                streams.addAll(serverStreams);
                Log.d(TAG, "Server " + server.name + " provided " + serverStreams.size() + " streams");
            } catch (Exception e) {
                Log.w(TAG, "Failed to process server " + server.name + ": " + e.getMessage());
            }
        }
        
        return streams;
    }
    
    /**
     * Method 3: Extract direct sources from various patterns
     */
    private List<ExtractedStream> extractDirectSources(MediaInfo mediaInfo) throws Exception {
        List<ExtractedStream> streams = new ArrayList<>();
        
        Log.d(TAG, "--- Method 3: Direct Source Extraction ---");
        
        // Try multiple direct source URLs
        String[] directUrls = {
            "https://vidsrc.to/embed/movie/" + mediaInfo.id,
            "https://vidsrc.me/embed/movie/" + mediaInfo.id,
            "https://2embed.to/embed/tmdb/movie?id=" + mediaInfo.id
        };
        
        if (!mediaInfo.isMovie()) {
            directUrls = new String[]{
                "https://vidsrc.to/embed/tv/" + mediaInfo.id + "/" + 
                    (mediaInfo.season != null ? mediaInfo.season : "1") + "/" + 
                    (mediaInfo.episode != null ? mediaInfo.episode : "1"),
                "https://vidsrc.me/embed/tv/" + mediaInfo.id + "/" + 
                    (mediaInfo.season != null ? mediaInfo.season : "1") + "/" + 
                    (mediaInfo.episode != null ? mediaInfo.episode : "1"),
                "https://2embed.to/embed/tmdb/tv?id=" + mediaInfo.id + 
                    "&s=" + (mediaInfo.season != null ? mediaInfo.season : "1") + 
                    "&e=" + (mediaInfo.episode != null ? mediaInfo.episode : "1")
            };
        }
        
        for (String directUrl : directUrls) {
            try {
                Log.d(TAG, "Trying direct source: " + directUrl);
                
                String content = fetchUrl(directUrl, false);
                List<ExtractedStream> directStreams = extractStreamsFromContent(content, directUrl);
                streams.addAll(directStreams);
                
                Log.d(TAG, "Direct source found " + directStreams.size() + " streams");
                
            } catch (Exception e) {
                Log.w(TAG, "Direct source failed " + directUrl + ": " + e.getMessage());
            }
        }
        
        return streams;
    }
    
    private String buildEmbedUrl(MediaInfo mediaInfo) {
        if (mediaInfo.isMovie()) {
            return EMBED_BASE_URL + "/movie?tmdb=" + mediaInfo.id;
        } else {
            return EMBED_BASE_URL + "/tv?tmdb=" + mediaInfo.id + 
                   "&season=" + (mediaInfo.season != null ? mediaInfo.season : "1") + 
                   "&episode=" + (mediaInfo.episode != null ? mediaInfo.episode : "1");
        }
    }
    
    private String fetchUrl(String url, boolean isAPI) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        
        // Set appropriate headers
        if (isAPI) {
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("Content-Type", "application/json");
        } else {
            connection.setRequestProperty("Accept", 
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        }
        
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Sec-Fetch-Dest", isAPI ? "empty" : "document");
        connection.setRequestProperty("Sec-Fetch-Mode", isAPI ? "cors" : "navigate");
        connection.setRequestProperty("Sec-Fetch-Site", "same-origin");
        
        if (!isAPI) {
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        }
        
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        
        int responseCode = connection.getResponseCode();
        Log.d(TAG, "HTTP response code for " + url + ": " + responseCode);
        
        if (responseCode != 200) {
            throw new Exception("HTTP error: " + responseCode + " for URL: " + url);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
        }
        reader.close();
        connection.disconnect();
        
        return response.toString();
    }
    
    private List<ExtractedStream> parseAPIResponse(JSONObject json) {
        List<ExtractedStream> streams = new ArrayList<>();
        
        try {
            // Try different JSON structures
            if (json.has("sources")) {
                JSONArray sources = json.getJSONArray("sources");
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject source = sources.getJSONObject(i);
                    ExtractedStream stream = parseSourceObject(source);
                    if (stream != null) {
                        streams.add(stream);
                    }
                }
            } else if (json.has("url")) {
                String url = json.getString("url");
                if (url != null && !url.isEmpty()) {
                    Bundle headers = createHeaders("https://vidsrc.net/");
                    String type = url.contains(".m3u8") ? "hls" : "mp4";
                    streams.add(new ExtractedStream(url, "auto", type, headers, true));
                }
            } else if (json.has("stream")) {
                JSONObject stream = json.getJSONObject("stream");
                ExtractedStream extractedStream = parseSourceObject(stream);
                if (extractedStream != null) {
                    streams.add(extractedStream);
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error parsing API response: " + e.getMessage());
        }
        
        return streams;
    }
    
    private ExtractedStream parseSourceObject(JSONObject source) {
        try {
            String url = source.optString("url", source.optString("file", ""));
            if (url.isEmpty()) return null;
            
            String quality = source.optString("quality", source.optString("label", "auto"));
            String type = url.contains(".m3u8") ? "hls" : "mp4";
            
            Bundle headers = createHeaders("https://vidsrc.net/");
            
            return new ExtractedStream(url, quality, type, headers, true);
            
        } catch (Exception e) {
            Log.w(TAG, "Error parsing source object: " + e.getMessage());
            return null;
        }
    }
    
    private List<ServerInfo> extractServersFromEmbed(String content) {
        List<ServerInfo> servers = new ArrayList<>();
        
        // Pattern 1: Standard server buttons with data-hash
        Pattern serverPattern = Pattern.compile("data-hash=[\"'](.*?)[\"'][^>]*class=[\"'][^\"']*server[^\"']*[\"'][^>]*>(.*?)<", Pattern.CASE_INSENSITIVE);
        Matcher serverMatcher = serverPattern.matcher(content);
        
        while (serverMatcher.find()) {
            ServerInfo server = new ServerInfo();
            server.dataHash = serverMatcher.group(1);
            server.name = serverMatcher.group(2).trim().replaceAll("<[^>]*>", "");
            
            if (!server.name.isEmpty() && !server.dataHash.isEmpty()) {
                servers.add(server);
                Log.d(TAG, "Found server: " + server.name + " (" + server.dataHash + ")");
            }
        }
        
        return servers;
    }
    
    private List<ExtractedStream> processServer(ServerInfo server, String referer) throws Exception {
        List<ExtractedStream> streams = new ArrayList<>();
        
        // Try different RCP endpoint patterns
        String[] rcpPatterns = {
            "https://vidsrc.net/rcp/" + server.dataHash,
            "https://vidsrc.net/api/rcp/" + server.dataHash,
            "https://vidsrc.net/source/" + server.dataHash
        };
        
        for (String rcpUrl : rcpPatterns) {
            try {
                Log.d(TAG, "Trying RCP: " + rcpUrl);
                
                String rcpContent = fetchUrl(rcpUrl, true);
                String streamUrl = extractStreamFromRCP(rcpContent);
                
                if (streamUrl != null && !streamUrl.isEmpty()) {
                    Log.d(TAG, "✓ Extracted stream from RCP: " + streamUrl);
                    
                    Bundle headers = createHeaders(referer);
                    String type = streamUrl.contains(".m3u8") ? "hls" : "mp4";
                    String quality = "auto";
                    
                    streams.add(new ExtractedStream(streamUrl, quality, type, headers, true));
                    break; // Found a working stream, no need to try other patterns
                }
                
            } catch (Exception e) {
                Log.w(TAG, "RCP pattern failed " + rcpUrl + ": " + e.getMessage());
            }
        }
        
        return streams;
    }
    
    private String extractStreamFromRCP(String rcpContent) {
        try {
            // Try to parse as JSON first
            try {
                JSONObject json = new JSONObject(rcpContent);
                if (json.has("url")) {
                    return json.getString("url");
                }
                if (json.has("source")) {
                    return json.getString("source");
                }
                if (json.has("file")) {
                    return json.getString("file");
                }
            } catch (Exception e) {
                // Not JSON, try other methods
            }
            
            // Look for src: pattern in RCP response
            Pattern srcPattern = Pattern.compile("src:\\s*[\"'](.*?)[\"']");
            Matcher srcMatcher = srcPattern.matcher(rcpContent);
            
            if (srcMatcher.find()) {
                String srcData = srcMatcher.group(1);
                Log.d(TAG, "Found src data: " + srcData);
                
                if (srcData.startsWith("http")) {
                    return srcData;
                }
            }
            
            // Look for direct URLs in the content
            Pattern urlPattern = Pattern.compile("(https?://[^\\s\"']+(?:\\.m3u8|\\.mp4)(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
            Matcher urlMatcher = urlPattern.matcher(rcpContent);
            
            if (urlMatcher.find()) {
                return urlMatcher.group(1);
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting stream from RCP: " + e.getMessage());
            return null;
        }
    }
    
    private List<ExtractedStream> extractStreamsFromContent(String content, String referer) {
        List<ExtractedStream> streams = new ArrayList<>();
        
        // Pattern 1: Direct .m3u8 URLs
        Pattern m3u8Pattern = Pattern.compile("(https?://[^\\s\"']+\\.m3u8(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher m3u8Matcher = m3u8Pattern.matcher(content);
        
        while (m3u8Matcher.find()) {
            String url = m3u8Matcher.group(1);
            Bundle headers = createHeaders(referer);
            streams.add(new ExtractedStream(url, "auto", "hls", headers, true));
            Log.d(TAG, "Found M3U8 stream: " + url);
        }
        
        // Pattern 2: Direct .mp4 URLs
        Pattern mp4Pattern = Pattern.compile("(https?://[^\\s\"']+\\.mp4(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher mp4Matcher = mp4Pattern.matcher(content);
        
        while (mp4Matcher.find()) {
            String url = mp4Matcher.group(1);
            Bundle headers = createHeaders(referer);
            streams.add(new ExtractedStream(url, "auto", "mp4", headers, true));
            Log.d(TAG, "Found MP4 stream: " + url);
        }
        
        return streams;
    }
    
    private static class ServerInfo {
        String name;
        String dataHash;
    }
}