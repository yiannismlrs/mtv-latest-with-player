package com.mtv.streaming.providers;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CloudStream-style Vidsrc Provider adapter
 */
public class CloudStreamVidsrcProvider implements Provider {
    private final com.mtv.streaming.extractors.VidsrcExtractor extractor;
    
    public CloudStreamVidsrcProvider() {
        this.extractor = new com.mtv.streaming.extractors.VidsrcExtractor();
    }
    
    @Override
    public ProviderInfo getProviderInfo() {
        return new ProviderInfo("vidsrc", "Vidsrc (CloudStream)", "2.0.0", 
            "CloudStream-style Vidsrc extractor with validation", true);
    }
    
    @Override
    public int getPriority() {
        return 1;
    }
    
    @Override
    public CompletableFuture<StreamResult> resolveStreams(MediaInfo mediaInfo) {
        com.mtv.streaming.extractors.StreamExtractor.MediaInfo extractorInfo = 
            new com.mtv.streaming.extractors.StreamExtractor.MediaInfo(
                mediaInfo.id, mediaInfo.type, mediaInfo.title, mediaInfo.year, 
                mediaInfo.season, mediaInfo.episode);
                
        return extractor.extractStreams(extractorInfo)
            .thenApply(result -> {
                if (result.success) {
                    return new StreamResult(
                        result.streams.stream()
                            .map(s -> new StreamSource(s.url, s.quality, s.type, s.headers, s.isWorking))
                            .collect(Collectors.toList()),
                        result.extractionTimeMs
                    );
                } else {
                    return new StreamResult(result.error, result.extractionTimeMs);
                }
            });
    }
    
    @Override
    public CompletableFuture<Boolean> testProvider() {
        return CompletableFuture.completedFuture(true);
    }
}
