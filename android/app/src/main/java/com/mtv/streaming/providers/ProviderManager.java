package com.mtv.streaming.providers;

import android.util.Log;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ProviderManager orchestrates multiple stream providers
 * Following Vega architecture pattern with fallback support
 */
public class ProviderManager {
    private static final String TAG = "ProviderManager";
    private static final long PROVIDER_TIMEOUT_MS = 15000; // 15 seconds timeout per provider
    
    private final List<Provider> providers;
    private static ProviderManager instance;
    
    private ProviderManager() {
        this.providers = new ArrayList<>();
        initializeProviders();
    }
    
    public static synchronized ProviderManager getInstance() {
        if (instance == null) {
            instance = new ProviderManager();
        }
        return instance;
    }
    
    /**
     * Initialize stream providers
     */
    private void initializeProviders() {
        Log.d(TAG, "Initializing stream providers...");
        
        try {
            // Add VidLink provider as primary
            providers.add(new VidLinkProvider());
            providers.add(new VidsrcProvider()); // Add working Vidsrc.to provider as fallback
            providers.add(new SuperStreamProvider()); // Add working SuperStream provider as fallback
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing providers: " + e.getMessage());
        }
        
        // Sort by priority
        providers.sort(Comparator.comparingInt(Provider::getPriority));
        
        Log.d(TAG, "Initialized " + providers.size() + " stream providers");
        for (Provider provider : providers) {
            Provider.ProviderInfo info = provider.getProviderInfo();
            Log.d(TAG, "Provider: " + info.name + " v" + info.version + " (enabled: " + info.enabled + ", priority: " + provider.getPriority() + ")");
        }
    }
    
    /**
     * Resolve streams using provider fallback system
     * Tries providers in order until one succeeds
     */
    public CompletableFuture<ProviderResult> resolveStreams(Provider.MediaInfo mediaInfo) {
        Log.d(TAG, "Resolving streams for: " + mediaInfo.title + " (" + mediaInfo.type + ")");
        
        return resolveWithProviders(mediaInfo, getEnabledProviders(), 0);
    }
    
    /**
     * Provider resolution result with additional metadata
     */
    public static class ProviderResult {
        public final boolean success;
        public final Provider.StreamSource bestSource;
        public final String providerUsed;
        public final String error;
        public final long totalTimeMs;
        public final int providersAttempted;
        
        public ProviderResult(Provider.StreamSource bestSource, String providerUsed, long totalTimeMs, int providersAttempted) {
            this.success = bestSource != null;
            this.bestSource = bestSource;
            this.providerUsed = providerUsed;
            this.error = null;
            this.totalTimeMs = totalTimeMs;
            this.providersAttempted = providersAttempted;
        }
        
        public ProviderResult(String error, long totalTimeMs, int providersAttempted) {
            this.success = false;
            this.bestSource = null;
            this.providerUsed = null;
            this.error = error;
            this.totalTimeMs = totalTimeMs;
            this.providersAttempted = providersAttempted;
        }
    }
    
    /**
     * Recursively try providers until one succeeds
     */
    private CompletableFuture<ProviderResult> resolveWithProviders(Provider.MediaInfo mediaInfo, List<Provider> enabledProviders, int providerIndex) {
        long startTime = System.currentTimeMillis();
        
        if (providerIndex >= enabledProviders.size()) {
            // All providers failed
            long totalTime = System.currentTimeMillis() - startTime;
            String error = "All " + enabledProviders.size() + " providers failed to resolve streams";
            Log.e(TAG, error);
            return CompletableFuture.completedFuture(new ProviderResult(error, totalTime, enabledProviders.size()));
        }
        
        Provider currentProvider = enabledProviders.get(providerIndex);
        Provider.ProviderInfo info = currentProvider.getProviderInfo();
        
        Log.d(TAG, "Trying provider " + (providerIndex + 1) + "/" + enabledProviders.size() + ": " + info.name);
        
        // Apply timeout to each provider attempt
        CompletableFuture<Provider.StreamResult> timeoutFuture = currentProvider.resolveStreams(mediaInfo)
            .orTimeout(PROVIDER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .exceptionally(throwable -> {
                String error = "Provider timeout or error: " + throwable.getMessage();
                Log.w(TAG, info.name + " failed: " + error);
                return new Provider.StreamResult(error, System.currentTimeMillis() - startTime);
            });
        
        return timeoutFuture.thenCompose(result -> {
            long providerTime = System.currentTimeMillis() - startTime;
            
            if (result.success && result.getBestSource() != null) {
                // Success! Return the result
                Provider.StreamSource bestSource = result.getBestSource();
                Log.d(TAG, "✓ " + info.name + " resolved stream successfully");
                Log.d(TAG, "Stream URL: " + bestSource.url);
                Log.d(TAG, "Quality: " + bestSource.quality);
                Log.d(TAG, "Type: " + bestSource.type);
                Log.d(TAG, "Resolve time: " + result.resolveTimeMs + "ms");
                
                return CompletableFuture.completedFuture(new ProviderResult(bestSource, info.name, providerTime, providerIndex + 1));
            } else {
                // This provider failed, try the next one
                String error = result.error != null ? result.error : "No streams found";
                Log.w(TAG, "✗ " + info.name + " failed: " + error + " (took " + result.resolveTimeMs + "ms)");
                
                // Try next provider
                return resolveWithProviders(mediaInfo, enabledProviders, providerIndex + 1);
            }
        });
    }
    
    /**
     * Get list of enabled providers
     */
    private List<Provider> getEnabledProviders() {
        List<Provider> enabled = new ArrayList<>();
        for (Provider provider : providers) {
            if (provider.getProviderInfo().enabled) {
                enabled.add(provider);
            }
        }
        return enabled;
    }
    
    /**
     * Get all available providers
     */
    public List<Provider> getAllProviders() {
        return new ArrayList<>(providers);
    }
    
    /**
     * Get enabled providers count
     */
    public int getEnabledProviderCount() {
        return getEnabledProviders().size();
    }
    
    /**
     * Test all providers
     */
    public CompletableFuture<List<Provider.ProviderInfo>> testAllProviders() {
        Log.d(TAG, "Testing all providers...");
        
        List<CompletableFuture<Provider.ProviderInfo>> tests = new ArrayList<>();
        
        for (Provider provider : providers) {
            Provider.ProviderInfo info = provider.getProviderInfo();
            
            CompletableFuture<Provider.ProviderInfo> test = provider.testProvider()
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(working -> {
                    Log.d(TAG, info.name + " test result: " + working);
                    return new Provider.ProviderInfo(info.id, info.name, info.version, info.description, working);
                })
                .exceptionally(throwable -> {
                    Log.w(TAG, info.name + " test failed: " + throwable.getMessage());
                    return new Provider.ProviderInfo(info.id, info.name, info.version, info.description, false);
                });
            
            tests.add(test);
        }
        
        return CompletableFuture.allOf(tests.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<Provider.ProviderInfo> results = new ArrayList<>();
                for (CompletableFuture<Provider.ProviderInfo> test : tests) {
                    results.add(test.join());
                }
                return results;
            });
    }
}
