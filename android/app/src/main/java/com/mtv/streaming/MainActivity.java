package com.mtv.streaming;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Configure WebView for better performance
        WebView webView = getBridge().getWebView();
        if (webView != null) {
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setDatabaseEnabled(true);
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            webSettings.setAppCacheEnabled(true);
            webSettings.setAllowFileAccess(true);
            webSettings.setAllowContentAccess(true);
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            
            // Set user agent for better compatibility
            webSettings.setUserAgentString(webSettings.getUserAgentString() + " MTVApp/1.0");
            
            // Set WebViewClient to handle URL loading
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url.startsWith("splayer://")) {
                        // Handle SPlayer URLs
                        try {
                            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
                            startActivity(intent);
                            return true;
                        } catch (Exception e) {
                            // If SPlayer is not installed, show message
                            android.widget.Toast.makeText(MainActivity.this, "SPlayer not installed. Please download from https://splayer.org", android.widget.Toast.LENGTH_LONG).show();
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
    }
}
