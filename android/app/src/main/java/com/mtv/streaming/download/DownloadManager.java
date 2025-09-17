package com.mtv.streaming.download;

import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Download manager for video files
 * Handles downloading video files from extracted streams
 */
public class DownloadManager {
    private static final String TAG = "VideoDownloadManager";
    private static DownloadManager instance;
    private final Context context;
    private final android.app.DownloadManager downloadManager;
    private final Map<Long, DownloadInfo> activeDownloads;
    private final List<DownloadListener> listeners;
    
    public interface DownloadListener {
        void onDownloadStarted(DownloadInfo download);
        void onDownloadProgress(DownloadInfo download, int progress);
        void onDownloadCompleted(DownloadInfo download, String filePath);
        void onDownloadFailed(DownloadInfo download, String error);
    }
    
    public static class DownloadInfo {
        public final long downloadId;
        public final String title;
        public final String url;
        public final String filename;
        public final String season;
        public final String episode;
        public final String type; // "movie" or "tv"
        public int progress;
        public String status;
        public String filePath;
        
        public DownloadInfo(long downloadId, String title, String url, String filename, String type, String season, String episode) {
            this.downloadId = downloadId;
            this.title = title;
            this.url = url;
            this.filename = filename;
            this.type = type;
            this.season = season;
            this.episode = episode;
            this.progress = 0;
            this.status = "Starting";
        }
    }
    
    private DownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        this.activeDownloads = new HashMap<>();
        this.listeners = new ArrayList<>();
        
        // Register broadcast receiver for download completion
        IntentFilter filter = new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        context.registerReceiver(downloadReceiver, filter);
    }
    
    public static synchronized DownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadManager(context);
        }
        return instance;
    }
    
    public CompletableFuture<DownloadInfo> downloadVideo(String vidlinkUrl, String title, String type, String season, String episode) {
        Log.d(TAG, "Starting download for: " + title);
        
        return VidLinkExtractor.extractDownloadableStreams(vidlinkUrl)
            .thenCompose(result -> {
                if (!result.success) {
                    CompletableFuture<DownloadInfo> future = new CompletableFuture<>();
                    future.completeExceptionally(new Exception(result.error));
                    return future;
                }
                
                VidLinkExtractor.ExtractedStream stream = result.getBestStream();
                if (stream == null) {
                    CompletableFuture<DownloadInfo> future = new CompletableFuture<>();
                    future.completeExceptionally(new Exception("No suitable stream found for download"));
                    return future;
                }
                
                return startDownload(stream, title, type, season, episode);
            });
    }
    
    private CompletableFuture<DownloadInfo> startDownload(VidLinkExtractor.ExtractedStream stream, String title, String type, String season, String episode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filename = stream.getFileName(title, season, episode);
                
                // Ensure the filename is valid
                if (!URLUtil.isValidUrl(stream.url)) {
                    throw new Exception("Invalid download URL");
                }
                
                // Create download request
                Request request = new Request(Uri.parse(stream.url));
                
                // Set headers
                for (Map.Entry<String, String> header : stream.headers.entrySet()) {
                    request.addRequestHeader(header.getKey(), header.getValue());
                }
                
                // Set destination
                File downloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                
                File destFile = new File(downloadsDir, filename);
                request.setDestinationUri(Uri.fromFile(destFile));
                
                // Set notification and visibility
                request.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setTitle("Downloading " + title);
                request.setDescription(filename);
                
                // Allow download over mobile and WiFi
                request.setAllowedNetworkTypes(Request.NETWORK_MOBILE | Request.NETWORK_WIFI);
                request.setAllowedOverRoaming(true);
                
                // Start download
                long downloadId = downloadManager.enqueue(request);
                
                DownloadInfo downloadInfo = new DownloadInfo(downloadId, title, stream.url, filename, type, season, episode);
                downloadInfo.filePath = destFile.getAbsolutePath();
                
                activeDownloads.put(downloadId, downloadInfo);
                
                // Notify listeners
                for (DownloadListener listener : listeners) {
                    listener.onDownloadStarted(downloadInfo);
                }
                
                Log.d(TAG, "Download started: " + filename + " (ID: " + downloadId + ")");
                
                return downloadInfo;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to start download: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    public void addDownloadListener(DownloadListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeDownloadListener(DownloadListener listener) {
        listeners.remove(listener);
    }
    
    public List<DownloadInfo> getActiveDownloads() {
        updateDownloadProgress();
        return new ArrayList<>(activeDownloads.values());
    }
    
    public List<DownloadInfo> getCompletedDownloads() {
        List<DownloadInfo> completed = new ArrayList<>();
        
        // Check for completed downloads in the app's download directory
        File downloadsDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
        if (downloadsDir.exists() && downloadsDir.isDirectory()) {
            File[] files = downloadsDir.listFiles((dir, name) -> name.endsWith(".mp4") || name.endsWith(".mkv"));
            
            if (files != null) {
                for (File file : files) {
                    // Create DownloadInfo for completed files
                    String filename = file.getName();
                    String title = extractTitleFromFilename(filename);
                    String[] seasonEpisode = extractSeasonEpisodeFromFilename(filename);
                    String type = seasonEpisode[0] != null ? "tv" : "movie";
                    
                    DownloadInfo info = new DownloadInfo(-1, title, "", filename, type, seasonEpisode[0], seasonEpisode[1]);
                    info.filePath = file.getAbsolutePath();
                    info.status = "Completed";
                    info.progress = 100;
                    
                    completed.add(info);
                }
            }
        }
        
        return completed;
    }
    
    private void updateDownloadProgress() {
        for (DownloadInfo download : activeDownloads.values()) {
            Cursor cursor = downloadManager.query(new android.app.DownloadManager.Query().setFilterById(download.downloadId));
            
            if (cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                int downloadedIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                
                int status = cursor.getInt(statusIndex);
                long downloaded = cursor.getLong(downloadedIndex);
                long total = cursor.getLong(totalIndex);
                
                if (total > 0) {
                    int progress = (int) ((downloaded * 100L) / total);
                    if (progress != download.progress) {
                        download.progress = progress;
                        
                        // Notify listeners of progress
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadProgress(download, progress);
                        }
                    }
                }
                
                // Update status
                switch (status) {
                    case android.app.DownloadManager.STATUS_PENDING:
                        download.status = "Pending";
                        break;
                    case android.app.DownloadManager.STATUS_RUNNING:
                        download.status = "Downloading";
                        break;
                    case android.app.DownloadManager.STATUS_PAUSED:
                        download.status = "Paused";
                        break;
                }
            }
            cursor.close();
        }
    }
    
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long downloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            
            DownloadInfo downloadInfo = activeDownloads.get(downloadId);
            if (downloadInfo != null) {
                Cursor cursor = downloadManager.query(new android.app.DownloadManager.Query().setFilterById(downloadId));
                
                if (cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIndex);
                    
                    if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                        downloadInfo.status = "Completed";
                        downloadInfo.progress = 100;
                        
                        // Notify listeners
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadCompleted(downloadInfo, downloadInfo.filePath);
                        }
                        
                        Log.d(TAG, "Download completed: " + downloadInfo.filename);
                    } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                        downloadInfo.status = "Failed";
                        
                        // Get failure reason
                        int reasonIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON);
                        int reason = cursor.getInt(reasonIndex);
                        String error = getFailureReason(reason);
                        
                        // Notify listeners
                        for (DownloadListener listener : listeners) {
                            listener.onDownloadFailed(downloadInfo, error);
                        }
                        
                        Log.e(TAG, "Download failed: " + downloadInfo.filename + " - " + error);
                    }
                }
                cursor.close();
                
                // Remove from active downloads when complete or failed
                if (downloadInfo.status.equals("Completed") || downloadInfo.status.equals("Failed")) {
                    activeDownloads.remove(downloadId);
                }
            }
        }
    };
    
    private String getFailureReason(int reason) {
        switch (reason) {
            case android.app.DownloadManager.ERROR_CANNOT_RESUME:
                return "Cannot resume download";
            case android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "Device not found";
            case android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "File already exists";
            case android.app.DownloadManager.ERROR_FILE_ERROR:
                return "File error";
            case android.app.DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "HTTP data error";
            case android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Insufficient storage space";
            case android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "Too many redirects";
            case android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "Unhandled HTTP code";
            case android.app.DownloadManager.ERROR_UNKNOWN:
            default:
                return "Unknown error";
        }
    }
    
    private String extractTitleFromFilename(String filename) {
        // Extract title from filename like "Movie_Title_720p.mp4" or "Show_Title_S01E05_720p.mp4"
        String nameWithoutExt = filename.replaceFirst("\\.[^.]*$", "");
        String[] parts = nameWithoutExt.split("_");
        
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part.matches("S\\d+E\\d+") || part.matches("\\d+p")) {
                break; // Stop at season/episode or quality
            }
            if (title.length() > 0) title.append(" ");
            title.append(part.replace("_", " "));
        }
        
        return title.toString();
    }
    
    private String[] extractSeasonEpisodeFromFilename(String filename) {
        // Returns [season, episode] or [null, null] if not a TV show
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("S(\\d+)E(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(filename);
        
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        
        return new String[]{null, null};
    }
    
    public void cancelDownload(long downloadId) {
        downloadManager.remove(downloadId);
        activeDownloads.remove(downloadId);
    }
    
    public void cleanup() {
        try {
            context.unregisterReceiver(downloadReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver not registered");
        }
    }
}
