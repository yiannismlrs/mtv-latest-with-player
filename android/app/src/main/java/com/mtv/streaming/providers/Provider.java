package com.mtv.streaming.providers;

import android.os.Bundle;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base Provider interface following Vega architecture pattern
 * Each provider handles stream resolution for different sources
 */
public interface Provider {
    
    /**
     * Provider metadata
     */
    class ProviderInfo {
        public final String id;
        public final String name;
        public final String version;
        public final String description;
        public final boolean enabled;
        
        public ProviderInfo(String id, String name, String version, String description, boolean enabled) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.description = description;
            this.enabled = enabled;
        }
    }
    
    /**
     * Stream source information
     */
    class StreamSource {
        public final String url;
        public final String quality;
        public final String type; // "hls", "mp4", "dash"
        public final Bundle headers;
        public final boolean isWorking;
        
        public StreamSource(String url, String quality, String type, Bundle headers, boolean isWorking) {
            this.url = url;
            this.quality = quality;
            this.type = type;
            this.headers = headers;
            this.isWorking = isWorking;
        }
        
        public String getMimeType() {
            switch (type.toLowerCase()) {
                case "hls":
                case "m3u8":
                    return "application/x-mpegURL";
                case "mp4":
                    return "video/mp4";
                case "dash":
                    return "application/dash+xml";
                default:
                    return "video/*";
            }
        }
    }
    
    /**
     * Stream resolution result
     */
    class StreamResult {
        public final boolean success;
        public final List<StreamSource> sources;
        public final String error;
        public final long resolveTimeMs;
        
        public StreamResult(List<StreamSource> sources, long resolveTimeMs) {
            this.success = sources != null && !sources.isEmpty();
            this.sources = sources;
            this.error = null;
            this.resolveTimeMs = resolveTimeMs;
        }
        
        public StreamResult(String error, long resolveTimeMs) {
            this.success = false;
            this.sources = null;
            this.error = error;
            this.resolveTimeMs = resolveTimeMs;
        }
        
        public StreamSource getBestSource() {
            if (sources == null || sources.isEmpty()) return null;
            
            // Prefer HLS streams, then MP4, prioritize working sources
            return sources.stream()
                .filter(s -> s.isWorking)
                .sorted((a, b) -> {
                    // Prioritize HLS
                    if (a.type.equals("hls") && !b.type.equals("hls")) return -1;
                    if (!a.type.equals("hls") && b.type.equals("hls")) return 1;
                    
                    // Then prioritize higher quality (basic sorting)
                    return compareQuality(b.quality, a.quality);
                })
                .findFirst()
                .orElse(sources.get(0));
        }
        
        private int compareQuality(String q1, String q2) {
            return Integer.compare(getQualityValue(q1), getQualityValue(q2));
        }
        
        private int getQualityValue(String quality) {
            if (quality == null) return 0;
            if (quality.contains("1080")) return 1080;
            if (quality.contains("720")) return 720;
            if (quality.contains("480")) return 480;
            if (quality.contains("360")) return 360;
            return 0;
        }
    }
    
    /**
     * Media information for stream resolution
     */
    class MediaInfo {
        public final String id;
        public final String type; // "movie" or "tv"
        public final String title;
        public final String year;
        public final Integer season;
        public final Integer episode;
        
        public MediaInfo(String id, String type, String title, String year) {
            this(id, type, title, year, null, null);
        }
        
        public MediaInfo(String id, String type, String title, String year, Integer season, Integer episode) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.year = year;
            this.season = season;
            this.episode = episode;
        }
        
        public boolean isMovie() {
            return "movie".equals(type);
        }
        
        public boolean isTVShow() {
            return "tv".equals(type);
        }
    }
    
    /**
     * Get provider information
     */
    ProviderInfo getProviderInfo();
    
    /**
     * Resolve streams for given media
     * @param mediaInfo Media to resolve streams for
     * @return CompletableFuture with stream resolution result
     */
    CompletableFuture<StreamResult> resolveStreams(MediaInfo mediaInfo);
    
    /**
     * Test if provider is currently working
     * @return CompletableFuture with test result
     */
    CompletableFuture<Boolean> testProvider();
    
    /**
     * Get provider priority (lower number = higher priority)
     * @return Priority value
     */
    default int getPriority() {
        return 100;
    }
}
