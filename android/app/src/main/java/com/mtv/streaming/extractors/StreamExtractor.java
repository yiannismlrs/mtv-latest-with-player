package com.mtv.streaming.extractors;

import android.os.Bundle;
import android.util.Log;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * CloudStream-style StreamExtractor base class
 */
public abstract class StreamExtractor {
    protected static final String TAG = "StreamExtractor";
    
    public static class ExtractedStream {
        public final String url;
        public final String quality;
        public final String type; // "hls", "mp4"
        public final Bundle headers;
        public final boolean isWorking;
        
        public ExtractedStream(String url, String quality, String type, Bundle headers, boolean isWorking) {
            this.url = url;
            this.quality = quality;
            this.type = type;
            this.headers = headers != null ? headers : new Bundle();
            this.isWorking = isWorking;
        }
        
        public String getMimeType() {
            return "hls".equals(type) || "m3u8".equals(type) ? "application/x-mpegURL" : "video/mp4";
        }
    }
    
    public static class ExtractionResult {
        public final boolean success;
        public final List<ExtractedStream> streams;
        public final String error;
        public final long extractionTimeMs;
        
        public ExtractionResult(List<ExtractedStream> streams, long extractionTimeMs) {
            this.success = streams != null && !streams.isEmpty();
            this.streams = streams;
            this.error = null;
            this.extractionTimeMs = extractionTimeMs;
        }
        
        public ExtractionResult(String error, long extractionTimeMs) {
            this.success = false;
            this.streams = null;
            this.error = error;
            this.extractionTimeMs = extractionTimeMs;
        }
        
        public ExtractedStream getBestStream() {
            if (!success || streams.isEmpty()) return null;
            return streams.stream()
                .filter(s -> s.isWorking)
                .sorted((a, b) -> {
                    if ("hls".equals(a.type) && !"hls".equals(b.type)) return -1;
                    if (!"hls".equals(a.type) && "hls".equals(b.type)) return 1;
                    return 0;
                })
                .findFirst()
                .orElse(streams.get(0));
        }
    }
    
    public static class MediaInfo {
        public final String id, type, title, year;
        public final Integer season, episode;
        
        public MediaInfo(String id, String type, String title, String year) {
            this(id, type, title, year, null, null);
        }
        
        public MediaInfo(String id, String type, String title, String year, Integer season, Integer episode) {
            this.id = id; this.type = type; this.title = title; 
            this.year = year; this.season = season; this.episode = episode;
        }
        
        public boolean isMovie() { return "movie".equals(type); }
    }
    
    public abstract String getName();
    public abstract CompletableFuture<ExtractionResult> extractStreams(MediaInfo mediaInfo);
    
    protected CompletableFuture<Boolean> validateStream(ExtractedStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(stream.url).openConnection();
                for (String key : stream.headers.keySet()) {
                    conn.setRequestProperty(key, stream.headers.getString(key));
                }
                conn.setConnectTimeout(5000);
                int code = conn.getResponseCode();
                conn.disconnect();
                return code == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    protected Bundle createHeaders(String referer) {
        Bundle headers = new Bundle();
        headers.putString("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
        headers.putString("Accept", "*/*");
        if (referer != null) headers.putString("Referer", referer);
        return headers;
    }
}
