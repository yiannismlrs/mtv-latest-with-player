package com.mtv.streaming;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {
    private WebView webView;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize WebView
        webView = findViewById(R.id.webview);
        
        // Set WebView background to dark color to eliminate white box
        webView.setBackgroundColor(Color.parseColor("#0D1117"));
        webView.setBackgroundResource(0); // Remove any default background
        
        // Configure WebView for better performance
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        
        // Set user agent for better compatibility
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " MTVApp/1.0");
        
        // Add JavaScript interface for native Android functions
        webView.addJavascriptInterface(new AndroidInterface(), "Android");
        
        // Set WebViewClient to handle URL loading
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Handle SPlayer intent URLs
                if (url.startsWith("intent://") && url.contains("splayer")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        // If SPlayer is not installed, open SPlayer website in external browser
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://splayer.dev/"));
                        startActivity(browserIntent);
                        return true;
                    }
                }
                // Handle SPlayer custom scheme URLs
                else if (url.startsWith("splayer://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        // If SPlayer is not installed, open website in external browser
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://splayer.dev/"));
                        startActivity(browserIntent);
                        return true;
                    }
                }
                // Handle external website URLs - open in system browser
                else if (url.startsWith("http://") || url.startsWith("https://")) {
                    // Check if it's not our local file
                    if (!url.contains("file:///android_asset/")) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(browserIntent);
                        return true; // Prevent loading in WebView
                    }
                }
                // Allow local files to load in WebView
                return false;
            }
        });
        
        // Load the local web app
        webView.loadUrl("file:///android_asset/www/index.html");
    }
    
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    // JavaScript Interface Class
    public class AndroidInterface {
        @JavascriptInterface
        public boolean isSPlayerInstalled() {
            boolean installed = SPlayerUtil.isSPlayerInstalled(MainActivity.this);
            android.util.Log.d("MTV_DEBUG", "SPlayer installed check: " + installed);
            return installed;
        }
        
        @JavascriptInterface
        public void openInSPlayer(String mediaId, String mediaType, String title, String year) {
            android.util.Log.d("MTV_DEBUG", "=== openInSPlayer called ===");
            android.util.Log.d("MTV_DEBUG", "Media ID: " + mediaId);
            android.util.Log.d("MTV_DEBUG", "Media Type: " + mediaType);
            android.util.Log.d("MTV_DEBUG", "Title: " + title);
            android.util.Log.d("MTV_DEBUG", "Year: " + year);
            
            // First check if SPlayer is installed
            boolean splayerInstalled = SPlayerUtil.isSPlayerInstalled(MainActivity.this);
            android.util.Log.d("MTV_DEBUG", "SPlayer installed check: " + splayerInstalled);
            
            if (splayerInstalled) {
                // SPlayer is installed - resolve streams and launch directly
                android.util.Log.d("MTV_DEBUG", "✓ SPlayer detected, starting stream resolution...");
                
                // Show loading message to user
                runOnUiThread(() -> showToast("Resolving stream..."));
                
                // Create MediaInfo for provider system
                com.mtv.streaming.providers.Provider.MediaInfo mediaInfo = 
                    new com.mtv.streaming.providers.Provider.MediaInfo(mediaId, mediaType, title, year);
                
                android.util.Log.d("MTV_DEBUG", "Created MediaInfo, starting provider resolution...");
                
                // Use ProviderManager to resolve streams
                com.mtv.streaming.providers.ProviderManager.getInstance()
                    .resolveStreams(mediaInfo)
                    .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // Add 30 second timeout
                    .thenAccept(result -> {
                        android.util.Log.d("MTV_DEBUG", "Provider resolution completed");
                        android.util.Log.d("MTV_DEBUG", "Success: " + result.success);
                        android.util.Log.d("MTV_DEBUG", "Provider used: " + result.providerUsed);
                        android.util.Log.d("MTV_DEBUG", "Total time: " + result.totalTimeMs + "ms");
                        android.util.Log.d("MTV_DEBUG", "Providers attempted: " + result.providersAttempted);
                        
                        runOnUiThread(() -> {
                            if (result.success && result.bestSource != null) {
                                android.util.Log.d("MTV_DEBUG", "✓ Stream successfully resolved by " + result.providerUsed);
                                android.util.Log.d("MTV_DEBUG", "Stream URL: " + result.bestSource.url);
                                android.util.Log.d("MTV_DEBUG", "Quality: " + result.bestSource.quality);
                                android.util.Log.d("MTV_DEBUG", "Type: " + result.bestSource.type);
                                android.util.Log.d("MTV_DEBUG", "MIME Type: " + result.bestSource.getMimeType());
                                android.util.Log.d("MTV_DEBUG", "Headers count: " + (result.bestSource.headers != null ? result.bestSource.headers.size() : 0));
                                
                                showToast("Stream resolved! Launching SPlayer...");
                                
                                // Launch SPlayer with resolved stream
                                boolean launched = SPlayerUtil.launchSPlayerDirect(MainActivity.this, 
                                    result.bestSource.url, 
                                    result.bestSource.getMimeType(), 
                                    result.bestSource.headers);
                                
                                if (!launched) {
                                    android.util.Log.w("MTV_DEBUG", "Direct launch failed, trying proxy method...");
                                    showToast("Trying alternative launch method...");
                                    SPlayerUtil.open(MainActivity.this, 
                                        result.bestSource.url, 
                                        result.bestSource.getMimeType(), 
                                        result.bestSource.headers);
                                } else {
                                    android.util.Log.d("MTV_DEBUG", "✓ SPlayer launched successfully!");
                                }
                            } else {
                                String errorMsg = result.error != null ? result.error : "Unknown error";
                                android.util.Log.e("MTV_DEBUG", "✗ Stream resolution failed: " + errorMsg);
                                android.util.Log.e("MTV_DEBUG", "Providers attempted: " + result.providersAttempted);
                                showToast("Failed to resolve stream: " + errorMsg);
                            }
                        });
                            
                            // Show more user-friendly error messages
                            if (errorMsg.contains("timeout") || errorMsg.contains("Timeout")) {
                                showToast("Stream resolution timed out. Please try again.");
                            } else if (errorMsg.contains("HTTP") || errorMsg.contains("network")) {
                                showToast("Network error. Check your internet connection.");
                            } else if (errorMsg.contains("No streams found")) {
                                showToast("No streams available for this content. Try another title.");
                            } else {
                                showToast("Failed to resolve stream. Please try again later.");
                            }
                    .exceptionally(throwable -> {
                        android.util.Log.e("MTV_DEBUG", "Provider system exception: " + throwable.getClass().getSimpleName());
                        android.util.Log.e("MTV_DEBUG", "Exception message: " + throwable.getMessage());
                        throwable.printStackTrace();
                        
                        runOnUiThread(() -> {
                            if (throwable instanceof java.util.concurrent.TimeoutException) {
                                showToast("Stream resolution timed out. Please try again.");
                            } else {
                                showToast("Stream resolution error. Please try again.");
                            }
                        });
                        return null;
                    });
                    
            } else {
                // SPlayer is NOT installed - open website
                android.util.Log.d("MTV_DEBUG", "✗ SPlayer not installed, opening website");
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://splayer.dev/"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                    android.util.Log.d("MTV_DEBUG", "✓ Opened SPlayer website in browser");
                } catch (Exception e) {
                    android.util.Log.e("MTV_DEBUG", "Failed to open browser: " + e.getMessage());
                    showToast("Please install SPlayer from Play Store");
                }
            }
        }
        
        private void showToast(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        }
        
        @JavascriptInterface
        public void openExternalUrl(String url) {
            android.util.Log.d("MTV_DEBUG", "openExternalUrl called with: " + url);
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            } catch (Exception e) {
                android.util.Log.e("MTV_DEBUG", "Failed to open external URL: " + e.getMessage());
            }
        }
    }
}
