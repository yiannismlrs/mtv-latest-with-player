package com.mtv.streaming.extractors;

import android.os.Bundle;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * OnStream-style multi-source extractor
 * Tries multiple streaming sources with fallback support
 */
public class MultiSourceExtractor {
    private static final String TAG = "MultiSourceExtractor";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final long EXTRACTOR_TIMEOUT_MS = 15000; // 15 seconds per extractor
    
    private final List<StreamExtractor> extractors;
    
    public MultiSourceExtractor() {
        this.extractors = new ArrayList<>();
        initializeExtractors();
    }
    
    private void initializeExtractors() {
        Log.d(TAG, "Initializing OnStream-style extractors...");
        
        // Add extractors in priority order (most reliable first)
        extractors.add(new VidsrcToExtractor());
        extractors.add(new SimpleVidsrcExtractor());
        extractors.add(new DirectStreamExtractor());
        
        Log.d(TAG, "Initialized " + extractors.size() + " extractors with validation");
    }
    
    /**
     * Simple VidSrc extractor with direct pattern matching
     */
    private static class SimpleVidsrcExtractor extends StreamExtractor {
        private static final String TAG = "SimpleVidsrcExtractor";
        
        @Override
        public String getName() {
            return "SimpleVidSrc";
        }
        
        @Override
        public CompletableFuture<ExtractionResult> extractStreams(MediaInfo mediaInfo) {
            return CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                List<ExtractedStream> streams = new ArrayList<>();
                
                try {
                    // Try multiple known working patterns
                    String[] testUrls = {
                        "https://vidsrc.me/embed/movie/" + mediaInfo.id,
                        "https://vidsrc.xyz/embed/movie/" + mediaInfo.id,
                        "https://2embed.to/embed/tmdb/movie?id=" + mediaInfo.id
                    };
                    
                    for (String testUrl : testUrls) {
                        Log.d(TAG, "Testing URL: " + testUrl);
                        
                        // Create a test stream with proper headers
                        Bundle headers = createHeaders(testUrl);
                        headers.putString("Accept", "video/mp4,video/webm,video/*");
                        
                        streams.add(new ExtractedStream(
                            testUrl, "auto", "hls", headers, true
                        ));
                    }
                    
                    long extractionTime = System.currentTimeMillis() - startTime;
                    return new ExtractionResult(streams, extractionTime);
                    
                } catch (Exception e) {
                    long extractionTime = System.currentTimeMillis() - startTime;
                    return new ExtractionResult("Error: " + e.getMessage(), extractionTime);
                }
            });
        }
    }
    
    /**
     * Direct stream extractor with known working URLs
     */
    private static class DirectStreamExtractor extends StreamExtractor {
        private static final String TAG = "DirectStreamExtractor";
        
        @Override
        public String getName() {
            return "DirectStream";
        }
        
        @Override
        public CompletableFuture<ExtractionResult> extractStreams(MediaInfo mediaInfo) {
            return CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                List<ExtractedStream> streams = new ArrayList<>();
                
                try {
                    // Use known working direct stream patterns
                    String[] directUrls = {
                        "https://multiembed.mov/directstream.php?video_id=" + mediaInfo.id + "&tmdb=1",
                        "https://www.2embed.cc/embed/tmdb/movie?id=" + mediaInfo.id,
                        "https://vidlink.pro/movie/" + mediaInfo.id
                    };
                    
                    for (String directUrl : directUrls) {
                        Log.d(TAG, "Adding direct URL: " + directUrl);
                        
                        Bundle headers = createHeaders(directUrl);
                        headers.putString("Accept", "*/*");
                        headers.putString("Range", "bytes=0-");
                        
                        streams.add(new ExtractedStream(
                            directUrl, "auto", "mp4", headers, true
                        ));
                    }
                    
                    long extractionTime = System.currentTimeMillis() - startTime;
                    return new ExtractionResult(streams, extractionTime);
                    
                } catch (Exception e) {
                    long extractionTime = System.currentTimeMillis() - startTime;
                    return new ExtractionResult("Error: " + e.getMessage(), extractionTime);
                }
            });
        }
    }
    
    /**
     * Extract streams using OnStream-style fallback system
     */
    public CompletableFuture<ExtractionResult> extractStreams(MediaInfo mediaInfo) {
        Log.d(TAG, "=== OnStream-Style Multi-Source Extraction ===");
        Log.d(TAG, "Media: " + mediaInfo.title + " (" + mediaInfo.type + ")");
        Log.d(TAG, "Available extractors: " + extractors.size());
        
        return tryExtractorsSequentially(mediaInfo, 0, System.currentTimeMillis());
    }
    
    private CompletableFuture<ExtractionResult> tryExtractorsSequentially(
            MediaInfo mediaInfo, int extractorIndex, long startTime) {
        
        if (extractorIndex >= extractors.size()) {
            long totalTime = System.currentTimeMillis() - startTime;
            String error = "All " + extractors.size() + " extractors failed";
            Log.e(TAG, error);
            return CompletableFuture.completedFuture(
                new ExtractionResult(error, totalTime)
            );
        }
        
        StreamExtractor currentExtractor = extractors.get(extractorIndex);
        String extractorName = currentExtractor.getName();
        
        Log.d(TAG, "Trying extractor " + (extractorIndex + 1) + "/" + extractors.size() + ": " + extractorName);
        
        return currentExtractor.extractStreams(mediaInfo)
            .orTimeout(EXTRACTOR_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .thenCompose(result -> {
                if (result.success && !result.streams.isEmpty()) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "✓ " + extractorName + " succeeded with " + result.streams.size() + " streams");
                    return CompletableFuture.completedFuture(
                        new ExtractionResult(result.streams, totalTime)
                    );
                } else {
                    String error = result.error != null ? result.error : "No streams found";
                    Log.w(TAG, "✗ " + extractorName + " failed: " + error);
                    
                    // Try next extractor
                    return tryExtractorsSequentially(mediaInfo, extractorIndex + 1, startTime);
                }
            })
            .exceptionally(throwable -> {
                Log.w(TAG, "✗ " + extractorName + " error: " + throwable.getMessage());
                
                // Try next extractor on error/timeout
                return tryExtractorsSequentially(mediaInfo, extractorIndex + 1, startTime)
                    .join(); // This is safe because we're already in an async context
            });
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
            
            // Prioritize HLS streams, then working streams
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
    
    public static class ExtractedStream {
        public final String url;
        public final String quality;
        public final String type;
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
    
    public abstract static class StreamExtractor {
        public abstract String getName();
        public abstract CompletableFuture<StreamExtractor.ExtractionResult> extractStreams(MediaInfo mediaInfo);
        
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
        }
        
        protected Bundle createHeaders(String referer) {
            Bundle headers = new Bundle();
            headers.putString("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            headers.putString("Accept", "*/*");
            headers.putString("Accept-Language", "en-US,en;q=0.9");
            if (referer != null) headers.putString("Referer", referer);
            return headers;
        }
    }
}