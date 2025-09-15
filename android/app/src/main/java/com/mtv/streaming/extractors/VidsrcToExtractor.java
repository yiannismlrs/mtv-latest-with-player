package com.mtv.streaming.extractors;

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
 * VidSrc.to extractor - Primary source
 */
public class VidsrcToExtractor extends MultiSourceExtractor.StreamExtractor {
    private static final String TAG = "VidsrcToExtractor";
    private static final String BASE_URL = "https://vidsrc.to/embed";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    public String getName() {
        return "VidSrc.to";
    }
    
    @Override
    public CompletableFuture<ExtractionResult> extractStreams(MultiSourceExtractor.MediaInfo mediaInfo) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                Log.d(TAG, "Extracting from VidSrc.to: " + mediaInfo.title);
                
                String embedUrl = buildEmbedUrl(mediaInfo);
                Log.d(TAG, "Embed URL: " + embedUrl);
                
                String html = fetchPage(embedUrl);
                if (html == null) {
                    return new ExtractionResult("Failed to fetch embed page", 
                        System.currentTimeMillis() - startTime);
                }
                
                List<ServerInfo> servers = extractServers(html);
                Log.d(TAG, "Found " + servers.size() + " servers");
                
                List<MultiSourceExtractor.ExtractedStream> streams = new ArrayList<>();
                
                for (ServerInfo server : servers) {
                    try {
                        String streamUrl = extractFromServer(server, embedUrl);
                        if (streamUrl != null) {
                            String type = streamUrl.contains(".m3u8") ? "hls" : "mp4";
                            Bundle headers = createStreamHeaders(streamUrl, embedUrl);
                            
                            streams.add(new MultiSourceExtractor.ExtractedStream(
                                streamUrl, "auto", type, headers, true
                            ));
                            
                            Log.d(TAG, "✓ Found stream: " + streamUrl);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Server failed: " + e.getMessage());
                    }
                }
                
                long extractionTime = System.currentTimeMillis() - startTime;
                
                if (!streams.isEmpty()) {
                    return new ExtractionResult(streams, extractionTime);
                } else {
                    return new ExtractionResult("No streams found", extractionTime);
                }
                
            } catch (Exception e) {
                long extractionTime = System.currentTimeMillis() - startTime;
                Log.e(TAG, "Extraction failed: " + e.getMessage());
                return new ExtractionResult("Error: " + e.getMessage(), extractionTime);
            }
        }, executor);
    }
    
    private String buildEmbedUrl(MultiSourceExtractor.MediaInfo mediaInfo) {
        if (mediaInfo.isMovie()) {
            return BASE_URL + "/movie/" + mediaInfo.id;
        } else {
            int season = mediaInfo.season != null ? mediaInfo.season : 1;
            int episode = mediaInfo.episode != null ? mediaInfo.episode : 1;
            return BASE_URL + "/tv/" + mediaInfo.id + "/" + season + "/" + episode;
        }
    }
    
    private String fetchPage(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            
            // Enhanced headers to bypass detection
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
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
            connection.setRequestProperty("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
            connection.setRequestProperty("sec-ch-ua-mobile", "?0");
            connection.setRequestProperty("sec-ch-ua-platform", "\"Windows\"");
            connection.setRequestProperty("DNT", "1");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Referer", "https://vidsrc.to/");
            
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP response: " + responseCode);
            
            if (responseCode != 200) {
                Log.w(TAG, "HTTP error: " + responseCode);
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
            
            return response.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch page: " + e.getMessage());
            return null;
        }
    }
    
    private List<ServerInfo> extractServers(String html) {
        List<ServerInfo> servers = new ArrayList<>();
        
        // Pattern for data-hash attributes
        Pattern serverPattern = Pattern.compile(
            "data-hash=[\"']([^\"']+)[\"'][^>]*>([^<]*)</[^>]*>", 
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = serverPattern.matcher(html);
        while (matcher.find()) {
            String dataHash = matcher.group(1);
            String serverName = matcher.group(2).replaceAll("<[^>]*>", "").trim();
            
            if (!dataHash.isEmpty() && !serverName.isEmpty()) {
                ServerInfo server = new ServerInfo();
                server.dataHash = dataHash;
                server.name = serverName;
                servers.add(server);
                
                Log.d(TAG, "Found server: " + serverName + " (" + dataHash + ")");
            }
        }
        
        // Fallback pattern for data-hash only
        if (servers.isEmpty()) {
            Pattern hashPattern = Pattern.compile("data-hash=[\"']([^\"']{10,})[\"']");
            Matcher hashMatcher = hashPattern.matcher(html);
            
            int count = 0;
            while (hashMatcher.find() && count < 5) {
                String dataHash = hashMatcher.group(1);
                ServerInfo server = new ServerInfo();
                server.dataHash = dataHash;
                server.name = "Server " + (count + 1);
                servers.add(server);
                count++;
            }
        }
        
        return servers;
    }
    
    private String extractFromServer(ServerInfo server, String referer) {
        try {
            // Try multiple RCP endpoints
            String[] rcpUrls = {
                "https://vidsrc.to/rcp/" + server.dataHash,
                "https://vidsrc.to/ajax/embed/episode/" + server.dataHash + "/sources"
            };
            
            for (String rcpUrl : rcpUrls) {
                String result = tryRcpUrl(rcpUrl, referer);
                if (result != null) {
                    return result;
                }
            }
            
            return null;
        } catch (Exception e) {
            Log.w(TAG, "RCP extraction failed: " + e.getMessage());
            return null;
        }
    }
    
    private String tryRcpUrl(String rcpUrl, String referer) {
        try {
            Log.d(TAG, "Trying RCP: " + rcpUrl);
            
            HttpURLConnection connection = (HttpURLConnection) new URL(rcpUrl).openConnection();
            
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Referer", referer);
            connection.setRequestProperty("Origin", "https://vidsrc.to");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\"");
            connection.setRequestProperty("sec-ch-ua-mobile", "?0");
            connection.setRequestProperty("sec-ch-ua-platform", "\"Windows\"");
            
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Log.w(TAG, "RCP failed: " + responseCode);
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
            
            return extractStreamFromResponse(response.toString());
            
        } catch (Exception e) {
            Log.w(TAG, "RCP URL failed: " + e.getMessage());
            return null;
        }
    }
    
    private String extractStreamFromResponse(String response) {
        Log.d(TAG, "Parsing RCP response: " + response.substring(0, Math.min(200, response.length())));
        
        // Method 1: Try JSON parsing first
        try {
            // Look for various JSON patterns
            String[] jsonKeys = {"url", "file", "source", "stream", "link"};
            for (String key : jsonKeys) {
                Pattern jsonPattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = jsonPattern.matcher(response);
                if (matcher.find()) {
                    String url = matcher.group(1);
                    if (url.startsWith("http") && (url.contains(".m3u8") || url.contains(".mp4") || url.contains("stream"))) {
                        Log.d(TAG, "Found stream in JSON key '" + key + "': " + url);
                        return url;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "JSON parsing failed, trying regex");
        }
        
        // Method 2: Direct URL pattern matching
        Pattern urlPattern = Pattern.compile("(https?://[^\\s\"'<>]+(?:\\.m3u8|\\.mp4)(?:\\?[^\\s\"'<>]*)?)", Pattern.CASE_INSENSITIVE);
        Matcher urlMatcher = urlPattern.matcher(response);
        
        if (urlMatcher.find()) {
            return urlMatcher.group(1);
        }
        
        // Method 3: Look for any streaming URL
        Pattern streamPattern = Pattern.compile("(https?://[^\\s\"'<>]*(?:stream|play|video)[^\\s\"'<>]*)", Pattern.CASE_INSENSITIVE);
        Matcher streamMatcher = streamPattern.matcher(response);
        
        if (streamMatcher.find()) {
            return streamMatcher.group(1);
        }
        
        return null;
    }
    
    private Bundle createStreamHeaders(String streamUrl, String referer) {
        Bundle headers = createHeaders(referer);
        
        try {
            URL url = new URL(streamUrl);
            String host = url.getHost();
            
            if (host != null && host.contains("vidsrc")) {
                headers.putString("Referer", "https://vidsrc.to/");
                headers.putString("Origin", "https://vidsrc.to");
            }
        } catch (Exception e) {
            headers.putString("Referer", "https://vidsrc.to/");
        }
        
        return headers;
    }
    
    private static class ServerInfo {
        String name;
        String dataHash;
    }
}