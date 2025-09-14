package com.mtv.streaming

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

object OpenInSPlayer {
    private const val TAG = "OpenInSPlayer"
    private const val SPLAYER_PACKAGE = "com.ttee.leeplayer"
    private const val SPLAYER_SITE = "https://splayer.dev/"

    fun open(context: Context, rawUrl: String) {
        // IMPORTANT: ensure this is a playable URL (mp4/m3u8), not an embed page.
        val uri = Uri.parse(rawUrl)

        val pm: PackageManager = context.packageManager
        val splayerInstalled = try {
            pm.getPackageInfo(SPLAYER_PACKAGE, 0)
            Log.d(TAG, "SPlayer package found: $SPLAYER_PACKAGE")
            true
        } catch (e: Exception) {
            Log.d(TAG, "SPlayer package not found: ${e.message}")
            false
        }
        Log.d(TAG, "SPlayer installed? $splayerInstalled | url=$rawUrl")

        if (splayerInstalled) {
            // Try to force SPlayer
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                // If MIME is known, set it; otherwise still try with ACTION_VIEW.
                // Many players prefer video/* explicitly:
                setDataAndType(uri, "video/*")
                `package` = SPLAYER_PACKAGE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched SPlayer with video URL")
                return
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "SPlayer could not handle URL, falling back. ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error launching SPlayer: ${e.message}")
            }
        } else {
            Log.d(TAG, "SPlayer not installed, using browser fallback.")
        }

        // Fallback: open SPlayer website in external browser (not in-app WebView)
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SPLAYER_SITE)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(webIntent)
            Log.d(TAG, "Opened SPlayer website in external browser")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SPlayer website: ${e.message}")
        }
    }

    // Helper method to check if SPlayer is installed
    fun isSPlayerInstalled(context: Context): Boolean {
        val pm: PackageManager = context.packageManager
        return try {
            pm.getPackageInfo(SPLAYER_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Helper method to get direct stream URL from embed URL
    fun getDirectStreamUrl(embedUrl: String): String {
        // For now, we'll pass the embed URL to SPlayer and let it handle extraction
        // In a production app, you might want to extract the actual stream URL
        // from the embed page first
        return embedUrl
    }
}
