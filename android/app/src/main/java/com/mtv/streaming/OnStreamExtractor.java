package com.mtv.streaming;

import android.os.Bundle;
import android.util.Log;
import com.mtv.streaming.extractors.MultiSourceExtractor;
import java.util.concurrent.CompletableFuture;

/**
 * OnStream-style main extractor that orchestrates multiple sources
 * This replaces the old VidsrcExtractor with a more robust multi-source approach
 */
public class OnStreamExtractor {
    private static final String TAG = "OnStreamExtractor";
    private final MultiSourceExtractor multiSourceExtractor;
    
    public OnStreamExtractor() {
        this.multiSourceExtractor = new MultiSourceExtractor();
    }
    
    public static class StreamResult {
        public final String url;
        public final String mimeType;
        public final Bundle headers;
        public final boolean success;
        public final String error;
        public final String sourceUsed;
        
        public StreamResult(String url, String mimeType, Bundle headers, String sourceUsed) {
            this.url = url;
            this.mimeType = mimeType;
            this.headers = headers;
            this.success = true;
            this.error = null;
            this.sourceUsed = sourceUsed;
        }
        
        public StreamResult(String error) {
            this.url = null;
            this.mimeType = null;
            this.headers = null;
            this.success = false;
            this.error = error;
            this.sourceUsed = null;
        }
    }
    
    public interface StreamCallback {
        void onStreamResolved(StreamResult result);
    }
    
    /**
     * Main entry point - OnStream-style extraction with multiple sources
     */
    public static void resolveStream(String mediaId, String mediaType, String title, String year, StreamCallback callback) {
        OnStreamExtractor extractor = new OnStreamExtractor();
        
        Log.d(TAG, "=== OnStream-Style Multi-Source Extraction ===");
        Log.d(TAG, "Media: " + title + " (" + mediaType + ")");
        Log.d(TAG, "TMDB ID: " + mediaId);
        Log.d(TAG, "Year: " + year);
        
        MultiSourceExtractor.MediaInfo mediaInfo = new MultiSourceExtractor.MediaInfo(
            mediaId, mediaType, title, year
        );
        
        extractor.multiSourceExtractor.extractStreams(mediaInfo)
            .thenAccept(result -> {
                if (result.success && result.getBestStream() != null) {
                    MultiSourceExtractor.ExtractedStream bestStream = result.getBestStream();
                    
                    Log.d(TAG, "✓ OnStream extraction successful!");
                    Log.d(TAG, "Stream URL: " + bestStream.url);
                    Log.d(TAG, "Stream Type: " + bestStream.type);
                    Log.d(TAG, "Stream Quality: " + bestStream.quality);
                    Log.d(TAG, "Extraction Time: " + result.extractionTimeMs + "ms");
                    
                    StreamResult streamResult = new StreamResult(
                        bestStream.url,
                        bestStream.getMimeType(),
                        bestStream.headers,
                        "Multi-Source"
                    );
                    
                    callback.onStreamResolved(streamResult);
                } else {
                    String error = result.error != null ? result.error : "No streams found from any source";
                    Log.e(TAG, "✗ OnStream extraction failed: " + error);
                    Log.e(TAG, "Extraction Time: " + result.extractionTimeMs + "ms");
                    
                    callback.onStreamResolved(new StreamResult(error));
                }
            })
            .exceptionally(throwable -> {
                String error = "OnStream extraction error: " + throwable.getMessage();
                Log.e(TAG, error);
                callback.onStreamResolved(new StreamResult(error));
                return null;
            });
    }
}