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
            
            // Build embed URL for stream extraction
            String embedUrl = buildEmbedUrl(mediaId, mediaType);
            android.util.Log.d("MTV_DEBUG", "Starting stream extraction from: " + embedUrl);
            
            runOnUiThread(() -> showToast("Extracting stream..."));
            
            // Use new StreamExtractor pipeline
            StreamExtractor.resolveStream(embedUrl, new StreamExtractor.StreamCallback() {
                @Override
                public void onStreamResolved(StreamExtractor.StreamResult result) {
                    runOnUiThread(() -> {
                        if (result.success) {
                            android.util.Log.d("MTV_DEBUG", "✓ Stream extraction successful");
                            android.util.Log.d("MTV_DEBUG", "Stream URL: " + result.url);
                            android.util.Log.d("MTV_DEBUG", "MIME Type: " + result.mimeType);
                            
                            try {
                                Intent playerIntent = new Intent(MainActivity.this, VideoPlayerActivity.class);
                                playerIntent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, result.url);
                                playerIntent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, title);
                                playerIntent.putExtra(VideoPlayerActivity.EXTRA_HEADERS, result.headers);
                                
                                startActivity(playerIntent);
                                android.util.Log.d("MTV_DEBUG", "✓ Opened video player with extracted stream");
                                showToast("Opening video player...");
                            } catch (Exception e) {
                                android.util.Log.e("MTV_DEBUG", "Failed to open video player: " + e.getMessage());
                                showToast("Failed to open video player: " + e.getMessage());
                            }
                        } else {
                            android.util.Log.e("MTV_DEBUG", "✗ Stream extraction failed: " + result.error);
                            showToast("Stream extraction failed: " + result.error);
                        }
                    });
                }
            });
        }
        
        private String buildEmbedUrl(String mediaId, String mediaType) {
            if ("movie".equals(mediaType)) {
                return "https://vidsrc.net/embed/movie?tmdb=" + mediaId;
            } else {
                // For TV shows, default to season 1, episode 1
                return "https://vidsrc.net/embed/tv?tmdb=" + mediaId + "&season=1&episode=1";
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