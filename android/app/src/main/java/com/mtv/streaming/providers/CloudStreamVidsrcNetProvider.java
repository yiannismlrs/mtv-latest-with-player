package com.mtv.streaming.providers;

import android.util.Log;
import com.mtv.streaming.extractors.StreamExtractor;
import com.mtv.streaming.extractors.VidsrcNetExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * CloudStream-style VidsrcNet provider
 * Uses the proven vidsrc.net extraction methodology
 */
public class CloudStreamVidsrcNetProvider implements Provider {
    private static final String TAG = "CloudStreamVidsrcNet";
    private final VidsrcNetExtractor extractor;
    
    public CloudStreamVidsrcNetProvider() {
        this.extractor = new VidsrcNetExtractor();
        Log.d(TAG, "CloudStream VidsrcNet provider initialized");
    }
    
    @Override
    public ProviderInfo getProviderInfo() {
        return new ProviderInfo(
            "vidsrcnet",
            "VidsrcNet",
            "1.0",
            "CloudStream-style VidsrcNet extractor with decryption",
            true
        );
    }
    
    @Override
    public int getPriority() {
        return 1; // High priority since it's proven to work
    }
    
    @Override
    public CompletableFuture<StreamResult> resolveStreams(MediaInfo mediaInfo) {
        long startTime = System.currentTimeMillis();
        
        Log.d(TAG, "Resolving streams for: " + mediaInfo.title);
        Log.d(TAG, "Media ID: " + mediaInfo.id);
        Log.d(TAG, "Type: " + mediaInfo.type);
        
        // Convert to extractor MediaInfo format
        StreamExtractor.MediaInfo extractorMediaInfo;
        if (!mediaInfo.isMovie() && mediaInfo.season != null && mediaInfo.episode != null) {
            extractorMediaInfo = new StreamExtractor.MediaInfo(
                mediaInfo.id,
                mediaInfo.type,
                mediaInfo.title,
                mediaInfo.year,
                mediaInfo.season,
                mediaInfo.episode
            );
        } else {
            extractorMediaInfo = new StreamExtractor.MediaInfo(
                mediaInfo.id,
                mediaInfo.type,
                mediaInfo.title,
                mediaInfo.year
            );
        }
        
        return extractor.extractStreams(extractorMediaInfo)
            .thenApply(result -> {
                long resolveTime = System.currentTimeMillis() - startTime;
                
                if (result.success && !result.streams.isEmpty()) {
                    // Convert extracted streams to StreamSource format
                    List<StreamSource> sources = new ArrayList<>();
                    
                    for (StreamExtractor.ExtractedStream stream : result.streams) {
                        StreamSource source = new StreamSource(
                            stream.url,
                            stream.quality,
                            stream.type,
                            stream.headers,
                            stream.isWorking
                        );
                        sources.add(source);
                        
                        Log.d(TAG, "Stream found: " + stream.quality + " " + stream.type);
                        Log.d(TAG, "URL: " + stream.url);
                        Log.d(TAG, "Validated: " + stream.isWorking);
                    }
                    
                    Log.d(TAG, "✓ VidsrcNet resolved " + sources.size() + " streams");
                    return new StreamResult(sources, resolveTime);
                } else {
                    String error = result.error != null ? result.error : "No streams found";
                    Log.w(TAG, "✗ VidsrcNet failed: " + error);
                    return new StreamResult(error, resolveTime);
                }
            })
            .exceptionally(throwable -> {
                long resolveTime = System.currentTimeMillis() - startTime;
                String error = "VidsrcNet extractor error: " + throwable.getMessage();
                Log.e(TAG, error);
                return new StreamResult(error, resolveTime);
            });
    }
    
    @Override
    public CompletableFuture<Boolean> testProvider() {
        Log.d(TAG, "Testing VidsrcNet provider...");
        
        // Test with a known working movie (The Matrix)
        MediaInfo testMedia = new MediaInfo("603", "movie", "The Matrix", "1999");
        
        return resolveStreams(testMedia)
            .thenApply(result -> {
                boolean working = result.success && result.getBestSource() != null;
                Log.d(TAG, "VidsrcNet test result: " + working + " (took " + result.resolveTimeMs + "ms)");
                if (working) {
                    Log.d(TAG, "Test stream URL: " + result.getBestSource().url);
                }
                return working;
            })
            .exceptionally(throwable -> {
                Log.w(TAG, "VidsrcNet test failed: " + throwable.getMessage());
                return false;
            });
    }
}
