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
 * SuperStream extractor - Alternative source
 */
public class SuperStreamExtractor extends MultiSourceExtractor.StreamExtractor {
    private static final String TAG = "SuperStreamExtractor";
    private static final String BASE_URL = "https://embed.su/embed";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    public String getName() {
        return "SuperStream";
    }
    
    @Override
    public CompletableFuture<ExtractionResult> extractStreams(MultiSourceExtractor.MediaInfo mediaInfo) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                Log.d(TAG, "Extracting from SuperStream: " + mediaInfo.title);
                
                String embedUrl = buildEmbedUrl(mediaInfo);
                Log.d(TAG, "Embed URL: " + embedUrl);
                
                String html = fetchPage(embedUrl);
                if (html == null) {
                    return new ExtractionResult("Failed to fetch embed page", 
                        System.currentTimeMillis() - startTime);
                }
                
                List<MultiSourceExtractor.ExtractedStream> streams = extractStreamsFromHtml(html, embedUrl);
                
                long extractionTime = System.currentTimeMillis() - startTime;
                
                if (!streams.isEmpty()) {
                    Log.d(TAG, "✓ Found " + streams.size() + " streams");
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
            
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", 
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Referer", "https://embed.su/");
            
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP response: " + responseCode);
            
            if (responseCode != 200) {
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
    
    private List<MultiSourceExtractor.ExtractedStream> extractStreamsFromHtml(String html, String referer) {
        List<MultiSourceExtractor.ExtractedStream> streams = new ArrayList<>();
        
        // Pattern 1: Direct M3U8 URLs
        Pattern m3u8Pattern = Pattern.compile("(https?://[^\\s\"']+\\.m3u8(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher m3u8Matcher = m3u8Pattern.matcher(html);
        
        while (m3u8Matcher.find()) {
            String streamUrl = m3u8Matcher.group(1);
            Bundle headers = createStreamHeaders(streamUrl, referer);
            
            streams.add(new MultiSourceExtractor.ExtractedStream(
                streamUrl, "auto", "hls", headers, true
            ));
            
            Log.d(TAG, "Found M3U8 stream: " + streamUrl);
        }
        
        // Pattern 2: Direct MP4 URLs
        Pattern mp4Pattern = Pattern.compile("(https?://[^\\s\"']+\\.mp4(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher mp4Matcher = mp4Pattern.matcher(html);
        
        while (mp4Matcher.find()) {
            String streamUrl = mp4Matcher.group(1);
            Bundle headers = createStreamHeaders(streamUrl, referer);
            
            streams.add(new MultiSourceExtractor.ExtractedStream(
                streamUrl, "auto", "mp4", headers, true
            ));
            
            Log.d(TAG, "Found MP4 stream: " + streamUrl);
        }
        
        // Pattern 3: JavaScript source assignments
        Pattern jsPattern = Pattern.compile("(?:source|src|url|file)\\s*[:=]\\s*[\"']([^\"']+\\.(?:m3u8|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher jsMatcher = jsPattern.matcher(html);
        
        while (jsMatcher.find()) {
            String streamUrl = jsMatcher.group(1);
            if (streamUrl.startsWith("http")) {
                String type = streamUrl.contains(".m3u8") ? "hls" : "mp4";
                Bundle headers = createStreamHeaders(streamUrl, referer);
                
                streams.add(new MultiSourceExtractor.ExtractedStream(
                    streamUrl, "auto", type, headers, true
                ));
                
                Log.d(TAG, "Found JS stream: " + streamUrl);
            }
        }
        
        return streams;
    }
    
    private Bundle createStreamHeaders(String streamUrl, String referer) {
        Bundle headers = createHeaders(referer);
        
        try {
            URL url = new URL(streamUrl);
            String host = url.getHost();
            
            if (host != null && host.contains("embed.su")) {
                headers.putString("Referer", "https://embed.su/");
                headers.putString("Origin", "https://embed.su");
            }
        } catch (Exception e) {
            headers.putString("Referer", "https://embed.su/");
        }
        
        return headers;
    }
}