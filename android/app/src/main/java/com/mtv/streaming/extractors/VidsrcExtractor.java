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
 * CloudStream-style VidsrcExtractor with robust stream extraction
 */
public class VidsrcExtractor extends StreamExtractor {
    private static final String TAG = "VidsrcExtractor";
    private static final String BASE_URL = "https://vidsrc.to/embed";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    public String getName() {
        return "Vidsrc";
    }
    
    @Override
    public CompletableFuture<ExtractionResult> extractStreams(MediaInfo mediaInfo) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                Log.d(TAG, "CloudStream-style extraction for: " + mediaInfo.title);
                
                String embedUrl = buildEmbedUrl(mediaInfo);
                Log.d(TAG, "Embed URL: " + embedUrl);
                
                // Stage 1: Get embed page
                String embedContent = fetchEmbedPage(embedUrl);
                
                // Stage 2: Extract all possible stream URLs using multiple techniques
                List<ExtractedStream> streams = extractAllStreams(embedContent, embedUrl);
                
                // Stage 3: Validate streams
                List<ExtractedStream> validatedStreams = validateStreams(streams);
                
                long extractionTime = System.currentTimeMillis() - startTime;
                
                if (!validatedStreams.isEmpty()) {
                    Log.d(TAG, "Successfully extracted " + validatedStreams.size() + " validated streams");
                    return new ExtractionResult(validatedStreams, extractionTime);
                } else {
                    return new ExtractionResult("No valid streams found", extractionTime);
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
            return BASE_URL + "/movie/" + mediaInfo.id;
        } else {
            if (mediaInfo.season != null && mediaInfo.episode != null) {
                return BASE_URL + "/tv/" + mediaInfo.id + "/" + mediaInfo.season + "/" + mediaInfo.episode;
            }
            return BASE_URL + "/tv/" + mediaInfo.id;
        }
    }
    
    private String fetchEmbedPage(String embedUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(embedUrl).openConnection();
        
        // Enhanced anti-bot headers like real browsers
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", 
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Sec-Fetch-Dest", "document");
        connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
        connection.setRequestProperty("Sec-Fetch-Site", "none");
        connection.setRequestProperty("Sec-Fetch-User", "?1");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        connection.setRequestProperty("Referer", "https://vidsrc.to/");
        
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        
        int responseCode = connection.getResponseCode();
        Log.d(TAG, "HTTP response code: " + responseCode);
        
        if (responseCode != 200) {
            String errorResponse = "";
            try {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse += errorLine;
                }
                errorReader.close();
            } catch (Exception e) {}
            
            Log.e(TAG, "HTTP error " + responseCode + ": " + errorResponse);
            throw new Exception("HTTP error: " + responseCode + " - " + errorResponse);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\\n");
        }
        reader.close();
        connection.disconnect();
        
        String content = response.toString();
        Log.d(TAG, "Fetched content length: " + content.length());
        
        // Check if we got blocked (common anti-bot responses)
        if (content.length() < 500 || 
            content.contains("histats.com") || 
            content.contains("blocked") || 
            content.contains("403") ||
            content.contains("cloudflare")) {
            Log.w(TAG, "Possible bot detection, content: " + content.substring(0, Math.min(200, content.length())));
        }
        
        return content;
    }
    
    private List<ExtractedStream> extractAllStreams(String content, String referer) {
        List<ExtractedStream> streams = new ArrayList<>();
        
        // CloudStream technique 1: Direct .m3u8 URLs
        extractDirectM3U8(content, streams, referer);
        
        // CloudStream technique 2: Direct .mp4 URLs
        extractDirectMP4(content, streams, referer);
        
        // CloudStream technique 3: JavaScript source URLs
        extractJavaScriptSources(content, streams, referer);
        
        // CloudStream technique 4: Data attributes and JSON
        extractDataSources(content, streams, referer);
        
        // CloudStream technique 5: Base64 encoded URLs
        extractBase64Sources(content, streams, referer);
        
        return streams;
    }
    
    private void extractDirectM3U8(String content, List<ExtractedStream> streams, String referer) {
        Pattern pattern = Pattern.compile("(https?://[^\\\\s\\\"']+\\\\.m3u8(?:\\\\?[^\\\\s\\\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String url = matcher.group(1);
            String quality = extractQuality(url, content);
            Bundle headers = createHeaders(referer);
            
            streams.add(new ExtractedStream(url, quality, "hls", headers, false)); // Will validate later
            Log.d(TAG, "Found direct M3U8: " + url);
        }
    }
    
    private void extractDirectMP4(String content, List<ExtractedStream> streams, String referer) {
        Pattern pattern = Pattern.compile("(https?://[^\\\\s\\\"']+\\\\.mp4(?:\\\\?[^\\\\s\\\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String url = matcher.group(1);
            String quality = extractQuality(url, content);
            Bundle headers = createHeaders(referer);
            
            streams.add(new ExtractedStream(url, quality, "mp4", headers, false));
            Log.d(TAG, "Found direct MP4: " + url);
        }
    }
    
    private void extractJavaScriptSources(String content, List<ExtractedStream> streams, String referer) {
        // Pattern for JavaScript video sources
        Pattern pattern = Pattern.compile("(?:source|src|url|file)\\\\s*[:=]\\\\s*[\\\"']([^\\\"']+(?:\\\\.m3u8|\\\\.mp4)[^\\\"']*)[\\\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String url = matcher.group(1);
            if (!url.startsWith("http")) continue;
            
            String type = url.contains(".m3u8") ? "hls" : "mp4";
            String quality = extractQuality(url, content);
            Bundle headers = createHeaders(referer);
            
            streams.add(new ExtractedStream(url, quality, type, headers, false));
            Log.d(TAG, "Found JS source: " + url);
        }
    }
    
    private void extractDataSources(String content, List<ExtractedStream> streams, String referer) {
        // Pattern for data attributes
        Pattern pattern = Pattern.compile("data-[a-zA-Z-]*src\\\\s*=\\\\s*[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String url = matcher.group(1);
            if (!url.startsWith("http") || (!url.contains(".m3u8") && !url.contains(".mp4"))) continue;
            
            String type = url.contains(".m3u8") ? "hls" : "mp4";
            String quality = extractQuality(url, content);
            Bundle headers = createHeaders(referer);
            
            streams.add(new ExtractedStream(url, quality, type, headers, false));
            Log.d(TAG, "Found data source: " + url);
        }
    }
    
    private void extractBase64Sources(String content, List<ExtractedStream> streams, String referer) {
        // Look for base64 encoded content that might contain URLs
        Pattern b64Pattern = Pattern.compile("(?:atob|base64)\\\\([\\\"']([A-Za-z0-9+/=]+)[\\\"']\\\\)", Pattern.CASE_INSENSITIVE);
        Matcher b64Matcher = b64Pattern.matcher(content);
        
        while (b64Matcher.find()) {
            try {
                String encoded = b64Matcher.group(1);
                byte[] decoded = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
                String decodedStr = new String(decoded);
                
                // Look for URLs in decoded content
                Pattern urlPattern = Pattern.compile("(https?://[^\\\\s\\\"']+(?:\\\\.m3u8|\\\\.mp4)(?:\\\\?[^\\\\s\\\"']*)?)", Pattern.CASE_INSENSITIVE);
                Matcher urlMatcher = urlPattern.matcher(decodedStr);
                
                while (urlMatcher.find()) {
                    String url = urlMatcher.group(1);
                    String type = url.contains(".m3u8") ? "hls" : "mp4";
                    String quality = extractQuality(url, decodedStr);
                    Bundle headers = createHeaders(referer);
                    
                    streams.add(new ExtractedStream(url, quality, type, headers, false));
                    Log.d(TAG, "Found base64 source: " + url);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to decode base64: " + e.getMessage());
            }
        }
    }
    
    private String extractQuality(String url, String context) {
        // Try URL first
        if (url.contains("1080")) return "1080p";
        if (url.contains("720")) return "720p";
        if (url.contains("480")) return "480p";
        if (url.contains("360")) return "360p";
        
        // Try context
        if (context != null) {
            if (context.contains("1080")) return "1080p";
            if (context.contains("720")) return "720p";
        }
        
        return "auto";
    }
    
    private List<ExtractedStream> validateStreams(List<ExtractedStream> streams) {
        List<ExtractedStream> validated = new ArrayList<>();
        
        for (ExtractedStream stream : streams) {
            try {
                boolean isWorking = validateStream(stream).get();
                if (isWorking) {
                    validated.add(new ExtractedStream(stream.url, stream.quality, stream.type, stream.headers, true));
                    Log.d(TAG, "✓ Validated stream: " + stream.url);
                } else {
                    Log.w(TAG, "✗ Failed validation: " + stream.url);
                }
            } catch (Exception e) {
                Log.w(TAG, "Validation error for " + stream.url + ": " + e.getMessage());
            }
        }
        
        return validated;
    }
}
