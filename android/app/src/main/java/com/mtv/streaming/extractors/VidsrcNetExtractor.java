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
import java.net.URLDecoder;

/**
 * VidsrcNet extractor based on proven TypeScript implementation
 * Uses vidsrc.net (not vidsrc.to) with proper decryption methods
 */
public class VidsrcNetExtractor extends StreamExtractor {
    private static final String TAG = "VidsrcNetExtractor";
    private static final String BASE_URL = "https://vidsrc.net/embed";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private String baseDom = "https://whisperingauroras.com";
    
    @Override
    public String getName() {
        return "VidsrcNet";
    }
    
    @Override
    public CompletableFuture<ExtractionResult> extractStreams(MediaInfo mediaInfo) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                Log.d(TAG, "VidsrcNet extraction for: " + mediaInfo.title);
                
                String embedUrl = buildEmbedUrl(mediaInfo);
                Log.d(TAG, "Embed URL: " + embedUrl);
                
                // Stage 1: Get embed page and extract servers
                String embedContent = fetchEmbedPage(embedUrl);
                ServerInfo serverInfo = extractServers(embedContent);
                
                Log.d(TAG, "Found " + serverInfo.servers.size() + " servers");
                Log.d(TAG, "Base domain: " + baseDom);
                
                // Stage 2: Process each server to get RCP data
                List<ExtractedStream> streams = new ArrayList<>();
                for (Server server : serverInfo.servers) {
                    try {
                        String rcpUrl = baseDom + "/rcp/" + server.dataHash;
                        Log.d(TAG, "Fetching RCP: " + rcpUrl);
                        
                        String rcpContent = fetchUrl(rcpUrl);
                        String streamUrl = extractStreamFromRCP(rcpContent);
                        
                        if (streamUrl != null && !streamUrl.isEmpty()) {
                            Log.d(TAG, "✓ Extracted stream: " + streamUrl);
                            
                            Bundle headers = createHeaders(baseDom);
                            String type = streamUrl.contains(".m3u8") ? "hls" : "mp4";
                            String quality = "auto";
                            
                            streams.add(new ExtractedStream(streamUrl, quality, type, headers, true));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to process server " + server.name + ": " + e.getMessage());
                    }
                }
                
                long extractionTime = System.currentTimeMillis() - startTime;
                
                if (!streams.isEmpty()) {
                    Log.d(TAG, "Successfully extracted " + streams.size() + " streams");
                    return new ExtractionResult(streams, extractionTime);
                } else {
                    return new ExtractionResult("No streams found", extractionTime);
                }
                
            } catch (Exception e) {
                long extractionTime = System.currentTimeMillis() - startTime;
                Log.e(TAG, "Extraction failed: " + e.getMessage());
                return new ExtractionResult("Extraction error: " + e.getMessage(), extractionTime);
            }
        }, executor);
    }
    
    private String buildEmbedUrl(MediaInfo mediaInfo) {
        if (mediaInfo.isMovie()) {
            return "https://vidsrc.net/embed/movie?tmdb=" + mediaInfo.id;
        } else {
            return "https://vidsrc.net/embed/tv?tmdb=" + mediaInfo.id + "&season=" + 
                   (mediaInfo.season != null ? mediaInfo.season : "1") + 
                   "&episode=" + (mediaInfo.episode != null ? mediaInfo.episode : "1");
        }
    }
    
    private String fetchEmbedPage(String embedUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(embedUrl).openConnection();
        
        // Real browser headers
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", 
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Sec-Fetch-Dest", "document");
        connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
        connection.setRequestProperty("Sec-Fetch-Site", "none");
        connection.setRequestProperty("Sec-Fetch-User", "?1");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        
        int responseCode = connection.getResponseCode();
        Log.d(TAG, "HTTP response code: " + responseCode);
        
        if (responseCode != 200) {
            throw new Exception("HTTP error: " + responseCode);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\\n");
        }
        reader.close();
        connection.disconnect();
        
        return response.toString();
    }
    
    private String fetchUrl(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Sec-Fetch-Dest", "empty");
        connection.setRequestProperty("Sec-Fetch-Mode", "cors");
        connection.setRequestProperty("Sec-Fetch-Site", "same-origin");
        connection.setRequestProperty("Referer", baseDom + "/");
        
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("HTTP error: " + responseCode);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\\n");
        }
        reader.close();
        connection.disconnect();
        
        return response.toString();
    }
    
    private ServerInfo extractServers(String content) {
        ServerInfo serverInfo = new ServerInfo();
        
        Log.d(TAG, "Extracting servers from content length: " + content.length());
        
        // Look for the iframe source which contains the actual player
        Pattern iframePattern = Pattern.compile("iframe[^>]*src=[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher iframeMatcher = iframePattern.matcher(content);
        
        String iframeSrc = null;
        while (iframeMatcher.find()) {
            String src = iframeMatcher.group(1);
            Log.d(TAG, "Found iframe src: " + src);
            if (src.contains("vidsrc") || src.contains("embed")) {
                iframeSrc = src;
                break;
            }
        }
        
        if (iframeSrc != null) {
            try {
                // Fetch the iframe content which should contain the servers
                String iframeContent = fetchUrl(iframeSrc);
                Log.d(TAG, "Iframe content length: " + iframeContent.length());
                
                // Update base domain from iframe URL
                URL url = new URL(iframeSrc);
                baseDom = url.getProtocol() + "://" + url.getHost();
                Log.d(TAG, "Updated base domain from iframe: " + baseDom);
                
                // Extract servers from iframe content
                extractServersFromContent(iframeContent, serverInfo);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch iframe content: " + e.getMessage());
                // Fallback: try to extract from original content
                extractServersFromContent(content, serverInfo);
            }
        } else {
            Log.w(TAG, "No iframe found, trying to extract from main content");
            extractServersFromContent(content, serverInfo);
        }
        
        return serverInfo;
    }
    
    private void extractServersFromContent(String content, ServerInfo serverInfo) {
        // Multiple patterns to find servers
        
        // Pattern 1: Standard server buttons with data-hash
        Pattern serverPattern1 = Pattern.compile("class=[\"'][^\"']*server[^\"']*[\"'][^>]*data-hash=[\"'](.*?)[\"'][^>]*>(.*?)<", Pattern.CASE_INSENSITIVE);
        Matcher serverMatcher1 = serverPattern1.matcher(content);
        while (serverMatcher1.find()) {
            Server server = new Server();
            server.dataHash = serverMatcher1.group(1);
            server.name = serverMatcher1.group(2).trim().replaceAll("<[^>]*>", "");
            if (!server.name.isEmpty() && !server.dataHash.isEmpty()) {
                serverInfo.servers.add(server);
                Log.d(TAG, "Found server (pattern 1): " + server.name + " (" + server.dataHash + ")");
            }
        }
        
        // Pattern 2: Alternative server structure
        Pattern serverPattern2 = Pattern.compile("data-hash=[\"'](.*?)[\"'][^>]*class=[\"'][^\"']*server[^\"']*[\"'][^>]*>(.*?)<", Pattern.CASE_INSENSITIVE);
        Matcher serverMatcher2 = serverPattern2.matcher(content);
        while (serverMatcher2.find()) {
            Server server = new Server();
            server.dataHash = serverMatcher2.group(1);
            server.name = serverMatcher2.group(2).trim().replaceAll("<[^>]*>", "");
            if (!server.name.isEmpty() && !server.dataHash.isEmpty()) {
                serverInfo.servers.add(server);
                Log.d(TAG, "Found server (pattern 2): " + server.name + " (" + server.dataHash + ")");
            }
        }
        
        // Pattern 3: Look for any data-hash attributes
        if (serverInfo.servers.isEmpty()) {
            Pattern hashPattern = Pattern.compile("data-hash=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher hashMatcher = hashPattern.matcher(content);
            int serverCount = 0;
            while (hashMatcher.find() && serverCount < 5) {
                Server server = new Server();
                server.dataHash = hashMatcher.group(1);
                server.name = "Server " + (serverCount + 1);
                if (!server.dataHash.isEmpty()) {
                    serverInfo.servers.add(server);
                    Log.d(TAG, "Found server (pattern 3): " + server.name + " (" + server.dataHash + ")");
                    serverCount++;
                }
            }
        }
        
        Log.d(TAG, "Total servers extracted: " + serverInfo.servers.size());
    }
    
    private String extractStreamFromRCP(String rcpContent) {
        try {
            // Look for src: pattern in RCP response
            Pattern srcPattern = Pattern.compile("src:\\s*[\"'](.*?)[\"']");
            Matcher srcMatcher = srcPattern.matcher(rcpContent);
            
            if (srcMatcher.find()) {
                String srcData = srcMatcher.group(1);
                Log.d(TAG, "Found src data: " + srcData);
                
                // Check if it starts with /prorcp/ (needs further processing)
                if (srcData.startsWith("/prorcp/")) {
                    String prorcpId = srcData.replace("/prorcp/", "");
                    return processPRORCP(prorcpId);
                } else if (srcData.startsWith("http")) {
                    // Direct URL
                    return srcData;
                }
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting stream from RCP: " + e.getMessage());
            return null;
        }
    }
    
    private String processPRORCP(String prorcpId) {
        try {
            String prorcpUrl = baseDom + "/prorcp/" + prorcpId;
            Log.d(TAG, "Processing PRORCP: " + prorcpUrl);
            
            String prorcpContent = fetchUrl(prorcpUrl);
            
            // Find the last script with .js extension
            Pattern scriptPattern = Pattern.compile("<script\\\\s+src=\\\"/([^\"]*\\\\.js)\\\\?\\\\_=([^\"]*)\\\"><\\\\/script>");
            Matcher scriptMatcher = scriptPattern.matcher(prorcpContent);
            
            String lastScript = null;
            while (scriptMatcher.find()) {
                String scriptPath = scriptMatcher.group(1) + "?_=" + scriptMatcher.group(2);
                if (!scriptPath.contains("cpt.js")) {  // Avoid captcha scripts
                    lastScript = scriptPath;
                }
            }
            
            if (lastScript != null) {
                String jsUrl = baseDom + "/" + lastScript;
                Log.d(TAG, "Fetching JS file: " + jsUrl);
                
                String jsContent = fetchUrl(jsUrl);
                
                // Extract decrypt function info
                Pattern decryptPattern = Pattern.compile("\\\\{\\\\}\\\\}window\\\\[([^\"]+)\\\\(\\\"([^\"]+)\\\"\\\\)");
                Matcher decryptMatcher = decryptPattern.matcher(jsContent);
                
                if (decryptMatcher.find()) {
                    String decryptFunc = decryptMatcher.group(1);
                    String decryptKey = decryptMatcher.group(2);
                    
                    Log.d(TAG, "Decrypt function: " + decryptFunc);
                    Log.d(TAG, "Decrypt key: " + decryptKey);
                    
                    // Extract the encrypted data from HTML using the decrypted ID
                    String elementId = simpleDecrypt(decryptKey, decryptFunc);
                    if (elementId != null) {
                        Pattern idPattern = Pattern.compile("id=[\"']" + elementId + "[\"'][^>]*>([^<]+)</");
                        Matcher idMatcher = idPattern.matcher(prorcpContent);
                        
                        if (idMatcher.find()) {
                            String encryptedData = idMatcher.group(1);
                            String finalUrl = simpleDecrypt(encryptedData, decryptKey);
                            
                            if (finalUrl != null && (finalUrl.startsWith("http") || finalUrl.startsWith("/"))) {
                                Log.d(TAG, "Final decrypted URL: " + finalUrl);
                                return finalUrl.startsWith("/") ? baseDom + finalUrl : finalUrl;
                            }
                        }
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error processing PRORCP: " + e.getMessage());
            return null;
        }
    }
    
    private String simpleDecrypt(String data, String method) {
        try {
            // Implement basic decryption methods based on the TypeScript decoder
            switch (method) {
                case "bMGyx71TzQLfdonN":
                    return bMGyx71TzQLfdonN(data);
                case "IGLImMhWrI":
                    return IGLImMhWrI(data);
                case "GTAxQyTyBx":
                    return GTAxQyTyBx(data);
                case "laM1dAi3vO":
                    return laM1dAi3vO(data);
                case "GuxKGDsA2T":
                    return GuxKGDsA2T(data);
                case "LXVUMCoAHJ":
                    return LXVUMCoAHJ(data);
                default:
                    Log.w(TAG, "Unknown decrypt method: " + method);
                    return data;
            }
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed: " + e.getMessage());
            return null;
        }
    }
    
    // Decryption methods ported from TypeScript
    private String bMGyx71TzQLfdonN(String input) {
        int chunkSize = 3;
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < input.length(); i += chunkSize) {
            chunks.add(input.substring(i, Math.min(i + chunkSize, input.length())));
        }
        StringBuilder result = new StringBuilder();
        for (int i = chunks.size() - 1; i >= 0; i--) {
            result.append(chunks.get(i));
        }
        return result.toString();
    }
    
    private String IGLImMhWrI(String input) {
        // Reverse
        String reversed = new StringBuilder(input).reverse().toString();
        // ROT13
        StringBuilder rot13 = new StringBuilder();
        for (char c : reversed.toCharArray()) {
            if (Character.isLetter(c)) {
                char base = Character.isUpperCase(c) ? 'A' : 'a';
                rot13.append((char) ((c - base + 13) % 26 + base));
            } else {
                rot13.append(c);
            }
        }
        // Reverse again
        String finalReversed = rot13.reverse().toString();
        // Base64 decode
        try {
            return new String(Base64.decode(finalReversed, Base64.DEFAULT));
        } catch (Exception e) {
            return null;
        }
    }
    
    private String GTAxQyTyBx(String input) {
        String reversed = new StringBuilder(input).reverse().toString();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < reversed.length(); i += 2) {
            result.append(reversed.charAt(i));
        }
        try {
            return new String(Base64.decode(result.toString(), Base64.DEFAULT));
        } catch (Exception e) {
            return null;
        }
    }
    
    private String laM1dAi3vO(String input) {
        String reversed = new StringBuilder(input).reverse().toString();
        String fixed = reversed.replace("-", "+").replace("_", "/");
        try {
            byte[] decoded = Base64.decode(fixed, Base64.DEFAULT);
            StringBuilder result = new StringBuilder();
            for (byte b : decoded) {
                result.append((char) (b - 5));
            }
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    private String GuxKGDsA2T(String input) {
        String reversed = new StringBuilder(input).reverse().toString();
        String fixed = reversed.replace("-", "+").replace("_", "/");
        try {
            byte[] decoded = Base64.decode(fixed, Base64.DEFAULT);
            StringBuilder result = new StringBuilder();
            for (byte b : decoded) {
                result.append((char) (b - 7));
            }
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    private String LXVUMCoAHJ(String input) {
        String reversed = new StringBuilder(input).reverse().toString();
        String fixed = reversed.replace("-", "+").replace("_", "/");
        try {
            byte[] decoded = Base64.decode(fixed, Base64.DEFAULT);
            StringBuilder result = new StringBuilder();
            for (byte b : decoded) {
                result.append((char) (b - 3));
            }
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    // Helper classes
    private static class ServerInfo {
        String title = "";
        List<Server> servers = new ArrayList<>();
    }
    
    private static class Server {
        String name;
        String dataHash;
    }
}
