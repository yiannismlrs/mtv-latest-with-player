package com.mtv.streaming;

import android.os.Bundle;
import android.util.Log;
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
 * Resolver for extracting direct stream URLs from vidsrc.to embed pages
 */
public class VidsrcResolver {
    private static final String TAG = "VidsrcResolver";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
    public static class StreamResult {
        public String streamUrl;
        public String mimeType;
        public Bundle headers;
        public String error;
        
        public StreamResult(String streamUrl, String mimeType, Bundle headers) {
            this.streamUrl = streamUrl;
            this.mimeType = mimeType;
            this.headers = headers;
            this.error = null;
        }
        
        public StreamResult(String error) {
            this.error = error;
            this.streamUrl = null;
            this.mimeType = null;
            this.headers = null;
        }
        
        public boolean isSuccess() {
            return error == null && streamUrl != null;
        }
    }
    
    public interface ResolverCallback {
        void onResult(StreamResult result);
    }
    
    /**
     * Resolve a vidsrc.to embed URL to get direct stream URL
     */
    public static void resolveStream(String embedUrl, ResolverCallback callback) {
        Log.d(TAG, "Resolving stream URL: " + embedUrl);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return extractStreamUrl(embedUrl);
            } catch (Exception e) {
                Log.e(TAG, "Error resolving stream: " + e.getMessage());
                return new StreamResult("Failed to resolve stream: " + e.getMessage());
            }
        }, executor).thenAccept(callback::onResult);
    }
    
    private static StreamResult extractStreamUrl(String embedUrl) throws Exception {
        Log.d(TAG, "Extracting stream from: " + embedUrl);
        
        // Create connection to vidsrc embed page
        URL url = new URL(embedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        // Set headers to mimic a browser request
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", 
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setRequestProperty("DNT", "1");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        
        int responseCode = connection.getResponseCode();
        Log.d(TAG, "Response code: " + responseCode);
        
        if (responseCode != 200) {
            return new StreamResult("HTTP error: " + responseCode);
        }
        
        // Read the response
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
        }
        reader.close();
        connection.disconnect();
        
        String html = response.toString();
        Log.d(TAG, "Received HTML length: " + html.length());
        
        // Try to extract direct stream URLs using various patterns
        String streamUrl = null;
        String mimeType = null;
        
        // Pattern 1: Look for .m3u8 URLs
        Pattern m3u8Pattern = Pattern.compile("(https?://[^\\s\"']+\\.m3u8(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
        Matcher m3u8Matcher = m3u8Pattern.matcher(html);
        if (m3u8Matcher.find()) {
            streamUrl = m3u8Matcher.group(1);
            mimeType = "application/x-mpegURL";
            Log.d(TAG, "Found M3U8 stream: " + streamUrl);
        }
        
        // Pattern 2: Look for .mp4 URLs if no .m3u8 found
        if (streamUrl == null) {
            Pattern mp4Pattern = Pattern.compile("(https?://[^\\s\"']+\\.mp4(?:\\?[^\\s\"']*)?)", Pattern.CASE_INSENSITIVE);
            Matcher mp4Matcher = mp4Pattern.matcher(html);
            if (mp4Matcher.find()) {
                streamUrl = mp4Matcher.group(1);
                mimeType = "video/mp4";
                Log.d(TAG, "Found MP4 stream: " + streamUrl);
            }
        }
        
        // Pattern 3: Look for source URLs in JavaScript
        if (streamUrl == null) {
            Pattern sourcePattern = Pattern.compile("(?:source|src|url)[\"'\\s]*:[\"'\\s]*(https?://[^\"'\\s]+)(?:\\.m3u8|\\.mp4)", Pattern.CASE_INSENSITIVE);
            Matcher sourceMatcher = sourcePattern.matcher(html);
            if (sourceMatcher.find()) {
                streamUrl = sourceMatcher.group(1);
                if (streamUrl.contains(".m3u8")) {
                    mimeType = "application/x-mpegURL";
                } else {
                    mimeType = "video/mp4";
                }
                Log.d(TAG, "Found source stream: " + streamUrl);
            }
        }
        
        if (streamUrl == null) {
            Log.w(TAG, "No direct stream URL found in HTML");
            // As a fallback, try the embed URL directly - some players can handle it
            return new StreamResult(embedUrl, "video/*", createHeaders(embedUrl));
        }
        
        // Create headers bundle for the stream request
        Bundle headers = createHeaders(streamUrl);
        
        Log.d(TAG, "Resolved stream - URL: " + streamUrl + ", MIME: " + mimeType);
        return new StreamResult(streamUrl, mimeType, headers);
    }
    
    private static Bundle createHeaders(String streamUrl) {
        Bundle headers = new Bundle();
        
        try {
            URL url = new URL(streamUrl);
            String host = url.getHost();
            
            // Standard headers for video streaming
            headers.putString("User-Agent", 
                "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
            headers.putString("Accept", "*/*");
            headers.putString("Accept-Encoding", "identity");
            headers.putString("Accept-Language", "en-US,en;q=0.9");
            
            // Set referer based on the stream host
            if (host.contains("vidsrc")) {
                headers.putString("Referer", "https://vidsrc.to/");
                headers.putString("Origin", "https://vidsrc.to");
            } else {
                headers.putString("Referer", streamUrl);
            }
            
            Log.d(TAG, "Created headers for: " + host);
            
        } catch (Exception e) {
            Log.w(TAG, "Error creating headers: " + e.getMessage());
        }
        
        return headers;
    }
}
