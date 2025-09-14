package com.mtv.streaming;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

/**
 * Java wrapper for SPlayer operations
 */
public class SPlayerUtil {
    private static final String TAG = "SPlayerUtil";
    private static final String SPLAYER_PACKAGE = "com.ttee.leeplayer";
    private static final String SPLAYER_SITE = "https://splayer.dev/";

    /**
     * Check if SPlayer is installed
     */
    public static boolean isSPlayerInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(SPLAYER_PACKAGE, 0);
            Log.d(TAG, "SPlayer package found: " + SPLAYER_PACKAGE);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "SPlayer package not found: " + e.getMessage());
            return false;
        }
    }

    /**
     * Open a stream URL in SPlayer or fallback to SPlayer website
     */
    public static void open(Context context, String streamUrl) {
        open(context, streamUrl, null, null);
    }
    
    /**
     * Open a stream URL in SPlayer with specific MIME type and headers
     * CloudStream-style with proxy fallback support
     */
    public static void open(Context context, String streamUrl, String mimeType, android.os.Bundle headers) {
        Log.d(TAG, "=== SPlayer Launch Attempt ===");
        Log.d(TAG, "Incoming URL: " + streamUrl);
        Log.d(TAG, "MIME type: " + mimeType);
        
        boolean splayerInstalled = isSPlayerInstalled(context);
        Log.d(TAG, "SPlayer installed: " + splayerInstalled);
        
        if (headers != null) {
            Log.d(TAG, "Headers provided: " + headers.keySet());
        } else {
            Log.d(TAG, "No headers provided");
        }
        
        if (splayerInstalled) {
            // Determine MIME type if not provided
            String actualMimeType = mimeType;
            if (actualMimeType == null) {
                if (streamUrl.contains(".m3u8")) {
                    actualMimeType = "application/x-mpegURL";
                } else if (streamUrl.contains(".mp4")) {
                    actualMimeType = "video/mp4";
                } else {
                    actualMimeType = "video/*";
                }
            }
            
            // First attempt: Direct launch with headers
            boolean directSuccess = attemptDirectLaunch(context, streamUrl, actualMimeType, headers);
            
            if (!directSuccess && headers != null && !headers.isEmpty()) {
                // Second attempt: Use local proxy for header handling
                Log.d(TAG, "Direct launch failed, trying proxy mode for headers");
                attemptProxyLaunch(context, streamUrl, actualMimeType, headers);
            }
            
            return;
        } else {
            Log.d(TAG, "SPlayer not installed, opening website");
        }

        // Fallback: open SPlayer website in external browser
        openSPlayerWebsite(context);
    }
    
    private static boolean attemptDirectLaunch(Context context, String streamUrl, String mimeType, android.os.Bundle headers) {
        try {
            Log.d(TAG, "--- Direct Launch Attempt ---");
            Log.d(TAG, "URL: " + streamUrl);
            Log.d(TAG, "MIME: " + mimeType);
            Log.d(TAG, "Target package: " + SPLAYER_PACKAGE);
            
            Uri uri = Uri.parse(streamUrl);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setPackage(SPLAYER_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Add headers if provided
            if (headers != null && !headers.isEmpty()) {
                intent.putExtra("android.intent.extra.HEADERS", headers);
                intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("https://vidsrc.net/"));
                Log.d(TAG, "Added " + headers.size() + " headers to direct intent");
                for (String key : headers.keySet()) {
                    Log.d(TAG, "Header: " + key + " = " + headers.get(key));
                }
            }
            
            // Verify intent can be resolved before launching
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                Log.d(TAG, "✓ Direct launch successful");
                Log.d(TAG, "Final URL: " + streamUrl);
                Log.d(TAG, "Final MIME: " + mimeType);
                return true;
            } else {
                Log.w(TAG, "✗ Intent could not be resolved - SPlayer may not support this MIME type");
                return false;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "✗ Direct launch failed: " + e.getMessage());
            return false;
        }
    }
    
    private static void attemptProxyLaunch(Context context, String streamUrl, String mimeType, android.os.Bundle headers) {
        try {
            Log.d(TAG, "Starting local proxy server for header handling...");
            
            // Start local proxy server
            com.mtv.streaming.proxy.LocalProxyServer proxy = com.mtv.streaming.proxy.LocalProxyServer.getInstance();
            String localUrl = proxy.startProxyForStream(streamUrl, headers);
            
            if (localUrl != null) {
                Log.d(TAG, "Proxy server started, launching SPlayer with local URL");
                
                Uri uri = Uri.parse(localUrl);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, mimeType);
                intent.setPackage(SPLAYER_PACKAGE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                context.startActivity(intent);
                Log.d(TAG, "✓ Proxy launch successful");
                Log.d(TAG, "Proxy URL: " + localUrl);
                Log.d(TAG, "Original URL: " + streamUrl);
                Log.d(TAG, "Proxy Mode: ENABLED");
                
            } else {
                Log.e(TAG, "Failed to start proxy server");
                openSPlayerWebsite(context);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "✗ Proxy launch failed: " + e.getMessage());
            openSPlayerWebsite(context);
        }
    }
    
    private static void openSPlayerWebsite(Context context) {
        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(SPLAYER_SITE));
        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(webIntent);
            Log.d(TAG, "Opened SPlayer website in external browser");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open SPlayer website: " + e.getMessage());
        }
    }
}
