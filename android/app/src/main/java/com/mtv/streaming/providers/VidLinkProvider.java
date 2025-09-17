package com.mtv.streaming.providers;

import android.util.Log;
import java.util.ArrayList;
import android.os.Bundle;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * VidLink provider implementation
 * Uses VidLink.pro embed URLs with TMDB IDs
 */
public class VidLinkProvider implements Provider {
    private static final String TAG = "VidLinkProvider";
    private static final String BASE_URL = "https://vidlink.pro";
    
    public VidLinkProvider() {
        Log.d(TAG, "VidLink provider initialized");
    }
    
    @Override
    public ProviderInfo getProviderInfo() {
        return new ProviderInfo(
            "vidlink",
            "VidLink",
            "1.0",
            "VidLink.pro embed player with direct TMDB integration",
            true
        );
    }
    
    @Override
    public int getPriority() {
        return 1; // High priority - simple and direct
    }
    
    @Override
    public CompletableFuture<StreamResult> resolveStreams(MediaInfo mediaInfo) {
        long startTime = System.currentTimeMillis();
        
        Log.d(TAG, "Resolving streams for: " + mediaInfo.title);
        Log.d(TAG, "Media ID: " + mediaInfo.id);
        Log.d(TAG, "Type: " + mediaInfo.type);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String embedUrl = buildEmbedUrl(mediaInfo);
                if (embedUrl == null) {
                    long resolveTime = System.currentTimeMillis() - startTime;
                    return new StreamResult("Invalid media info for VidLink", resolveTime);
                }
                
                Log.d(TAG, "VidLink embed URL: " + embedUrl);
                
                // Create StreamSource with embed URL
                List<StreamSource> sources = new ArrayList<>();
                Bundle headers = new Bundle();
                headers.putString("Referer", "https://vidlink.pro/");
                headers.putString("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                
                StreamSource source = new StreamSource(
                    embedUrl,
                    "1080p", // VidLink provides multiple qualities
                    "embed", // This is an embed URL
                    headers,
                    true
                );
                sources.add(source);
                
                long resolveTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, "✓ VidLink resolved embed URL successfully");
                
                return new StreamResult(sources, resolveTime);
                
            } catch (Exception e) {
                long resolveTime = System.currentTimeMillis() - startTime;
                String error = "VidLink error: " + e.getMessage();
                Log.e(TAG, error);
                return new StreamResult(error, resolveTime);
            }
        });
    }
    
    /**
     * Build embed URL based on media type and info
     */
    private String buildEmbedUrl(MediaInfo mediaInfo) {
        if (mediaInfo.id == null || mediaInfo.id.trim().isEmpty()) {
            Log.e(TAG, "Missing TMDB ID");
            return null;
        }
        
        if ("movie".equals(mediaInfo.type)) {
            // Movie: https://vidlink.pro/movie/{tmdbId}
            return BASE_URL + "/movie/" + mediaInfo.id;
        } else if ("tv".equals(mediaInfo.type)) {
            // TV Show: https://vidlink.pro/tv/{tmdbId}/{season}/{episode}
            if (mediaInfo.season != null && mediaInfo.episode != null) {
                return BASE_URL + "/tv/" + mediaInfo.id + "/" + mediaInfo.season + "/" + mediaInfo.episode;
            } else {
                Log.e(TAG, "Missing season/episode for TV show");
                return null;
            }
        } else {
            Log.e(TAG, "Unsupported media type: " + mediaInfo.type);
            return null;
        }
    }
    
    @Override
    public CompletableFuture<Boolean> testProvider() {
        Log.d(TAG, "Testing VidLink provider...");
        
        // Test with a known working movie (Furious 7 - TMDB ID: 168259)
        MediaInfo testMedia = new MediaInfo("168259", "movie", "Furious 7", "2015");
        
        return resolveStreams(testMedia)
            .thenApply(result -> {
                boolean working = result.success && result.getBestSource() != null;
                Log.d(TAG, "VidLink test result: " + working + " (took " + result.resolveTimeMs + "ms)");
                if (working) {
                    Log.d(TAG, "Test embed URL: " + result.getBestSource().url);
                }
                return working;
            })
            .exceptionally(throwable -> {
                Log.w(TAG, "VidLink test failed: " + throwable.getMessage());
                return false;
            });
    }
}
