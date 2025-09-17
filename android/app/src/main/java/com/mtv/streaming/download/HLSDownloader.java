package com.mtv.streaming.download;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URLDecoder;

/**
 * Simple HLS Downloader that converts M3U8 playlists to MP4 files
 * Downloads all video segments and combines them into a single file
 */
public class HLSDownloader {
    private static final String TAG = "HLSDownloader";
    private static HLSDownloader instance;
    private final Context context;
    private final ExecutorService executor;
    
    public interface HLSDownloadListener {
        void onProgress(int segmentIndex, int totalSegments, int overallProgress);
        void onCompleted(String outputFilePath);
        void onFailed(String error);
    }
    
    public static class HLSDownloadInfo {
        public final String title;
        public final String m3u8Url;
        public final String outputPath;
        public final Bundle headers;
        public int totalSegments;
        public int downloadedSegments;
        public boolean isCompleted;
        public String error;
        
        public HLSDownloadInfo(String title, String m3u8Url, String outputPath, Bundle headers) {
            this.title = title;
            this.m3u8Url = m3u8Url;
            this.outputPath = outputPath;
            this.headers = headers;
            this.totalSegments = 0;
            this.downloadedSegments = 0;
            this.isCompleted = false;
        }
    }
    
    private HLSDownloader(Context context) {
        this.context = context;
        this.executor = Executors.newFixedThreadPool(4); // 4 concurrent downloads
    }
    
    public static synchronized HLSDownloader getInstance(Context context) {
        if (instance == null) {
            instance = new HLSDownloader(context);
        }
        return instance;
    }
    
    /**
     * Download HLS stream and convert to MP4
     */
    public CompletableFuture<String> downloadHLS(String m3u8Content, String title, String baseUrl, Bundle headers) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "=== Starting HLS Download ===");
                Log.d(TAG, "Title: " + title);
                Log.d(TAG, "Base URL: " + baseUrl);
                
                // Parse M3U8 content to get video segments
                List<String> segmentUrls = parseM3U8(m3u8Content, baseUrl);
                
                if (segmentUrls.isEmpty()) {
                    throw new RuntimeException("No video segments found in M3U8 file");
                }
                
                Log.d(TAG, "Found " + segmentUrls.size() + " video segments");
                
                // Create output file
                File outputDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MTV_Downloads");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                
                String filename = title.replaceAll("[^a-zA-Z0-9\\s]", "").trim().replaceAll("\\s+", "_") + ".mp4";
                File outputFile = new File(outputDir, filename);
                
                // Download and combine segments
                downloadAndCombineSegments(segmentUrls, outputFile, headers);
                
                Log.d(TAG, "✓ HLS download completed: " + outputFile.getAbsolutePath());
                return outputFile.getAbsolutePath();
                
            } catch (Exception e) {
                Log.e(TAG, "HLS download failed: " + e.getMessage());
                throw new RuntimeException("HLS download failed: " + e.getMessage());
            }
        }, executor);
    }
    
    /**
     * Parse M3U8 content to extract segment URLs
     */
    private List<String> parseM3U8(String m3u8Content, String baseUrl) {
        List<String> segmentUrls = new ArrayList<>();
        
        try {
            Log.d(TAG, "Parsing M3U8 content...");
            Log.d(TAG, "M3U8 preview: " + m3u8Content.substring(0, Math.min(500, m3u8Content.length())));
            Log.d(TAG, "Base URL: " + baseUrl);
            
            String[] lines = m3u8Content.split("\n");
            
            // Check if this is a master playlist (contains other playlists)
            boolean isMasterPlaylist = m3u8Content.contains("EXT-X-STREAM-INF");
            
            if (isMasterPlaylist) {
                Log.d(TAG, "Master playlist detected, extracting best quality stream");
                
                // Find the highest quality stream (1080p from your example)
                String bestStreamUrl = null;
                int bestBandwidth = 0;
                
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        // Extract bandwidth
                        Pattern bandwidthPattern = Pattern.compile("BANDWIDTH=(\\d+)");
                        Matcher matcher = bandwidthPattern.matcher(line);
                        
                        int bandwidth = 0;
                        if (matcher.find()) {
                            bandwidth = Integer.parseInt(matcher.group(1));
                        }
                        
                        // Get the next line which should be the stream URL
                        if (i + 1 < lines.length) {
                            String streamUrl = lines[i + 1].trim();
                            if (!streamUrl.startsWith("#") && bandwidth > bestBandwidth) {
                                bestBandwidth = bandwidth;
                                bestStreamUrl = streamUrl;
                            }
                        }
                    }
                }
                
                if (bestStreamUrl != null) {
                    Log.d(TAG, "Best quality stream: " + bestStreamUrl + " (bandwidth: " + bestBandwidth + ")");
                    
                    // Convert relative URL to absolute with proper host extraction
                    String fullStreamUrl = resolveUrlWithHost(bestStreamUrl, baseUrl, m3u8Content);
                    Log.d(TAG, "Full stream URL: " + fullStreamUrl);
                    
                    // Now fetch the actual segment playlist
                    String segmentPlaylist = fetchM3U8Content(fullStreamUrl);
                    if (segmentPlaylist != null) {
                        return parseM3U8(segmentPlaylist, getBaseUrlFromFullUrl(fullStreamUrl));
                    }
                }
            } else {
                Log.d(TAG, "Segment playlist detected, extracting video segments");
                
                // This is a segment playlist, extract .ts files
                for (String line : lines) {
                    line = line.trim();
                    
                    if (!line.startsWith("#") && !line.isEmpty()) {
                        // This should be a segment URL
                        String segmentUrl = resolveUrlWithHost(line, baseUrl, m3u8Content);
                        segmentUrls.add(segmentUrl);
                        Log.d(TAG, "Found segment: " + segmentUrl);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing M3U8: " + e.getMessage());
        }
        
        Log.d(TAG, "Extracted " + segmentUrls.size() + " video segments");
        return segmentUrls;
    }
    
    /**
     * Resolve relative URLs to absolute URLs with host extraction from M3U8 content
     */
    private String resolveUrlWithHost(String url, String baseUrl, String m3u8Content) {
        if (url.startsWith("http")) {
            return url; // Already absolute
        }
        
        try {
            // Extract host from M3U8 content if available
            String extractedHost = extractHostFromM3U8(m3u8Content);
            if (extractedHost != null) {
                Log.d(TAG, "Using extracted host: " + extractedHost);
                return extractedHost + url;
            }
            
            if (url.startsWith("/")) {
                // Absolute path - extract host from base URL
                URL base = new URL(baseUrl);
                return base.getProtocol() + "://" + base.getHost() + url;
            } else {
                // Relative path - get directory from base URL
                String baseDir = baseUrl.substring(0, baseUrl.lastIndexOf("/") + 1);
                return baseDir + url;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error resolving URL: " + e.getMessage());
            return baseUrl + "/" + url;
        }
    }
    
    /**
     * Extract host from M3U8 content (looks for host parameter)
     */
    private String extractHostFromM3U8(String m3u8Content) {
        try {
            // Look for host parameter in the M3U8 content
            Pattern hostPattern = Pattern.compile("host=([^&\\s]+)");
            Matcher matcher = hostPattern.matcher(m3u8Content);
            
            if (matcher.find()) {
                String encodedHost = matcher.group(1);
                // URL decode the host
                String decodedHost = java.net.URLDecoder.decode(encodedHost, "UTF-8");
                Log.d(TAG, "Extracted host from M3U8: " + decodedHost);
                return decodedHost;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting host from M3U8: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get base URL from full URL (remove filename)
     */
    private String getBaseUrlFromFullUrl(String fullUrl) {
        try {
            return fullUrl.substring(0, fullUrl.lastIndexOf("/") + 1);
        } catch (Exception e) {
            return fullUrl;
        }
    }
    
    /**
     * Fetch M3U8 content from URL
     */
    private String fetchM3U8Content(String url) {
        try {
            Log.d(TAG, "Fetching M3U8 from: " + url);
            
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            
            // Set headers based on the URL host
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            
            // Set appropriate referer based on URL
            if (url.contains("breezeflash")) {
                connection.setRequestProperty("Referer", "https://videostr.net/");
                connection.setRequestProperty("Origin", "https://videostr.net");
            } else if (url.contains("vidlink")) {
                connection.setRequestProperty("Referer", "https://vidlink.pro/");
                connection.setRequestProperty("Origin", "https://vidlink.pro");
            } else if (url.contains("vidsrc")) {
                connection.setRequestProperty("Referer", "https://vidsrc.to/");
                connection.setRequestProperty("Origin", "https://vidsrc.to");
            } else {
                connection.setRequestProperty("Referer", "https://videostr.net/");
                connection.setRequestProperty("Origin", "https://videostr.net");
            }
            
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "M3U8 fetch response: " + responseCode);
            
            if (responseCode != 200) {
                Log.w(TAG, "Failed to fetch M3U8: " + responseCode);
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            connection.disconnect();
            
            return content.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching M3U8 content: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Download all segments and combine into single MP4 file
     */
    private void downloadAndCombineSegments(List<String> segmentUrls, File outputFile, Bundle headers) throws Exception {
        Log.d(TAG, "Downloading " + segmentUrls.size() + " segments...");
        
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        
        try {
            for (int i = 0; i < segmentUrls.size(); i++) {
                String segmentUrl = segmentUrls.get(i);
                
                Log.d(TAG, "Downloading segment " + (i + 1) + "/" + segmentUrls.size() + ": " + segmentUrl);
                
                byte[] segmentData = downloadSegment(segmentUrl, headers);
                if (segmentData != null) {
                    outputStream.write(segmentData);
                    outputStream.flush();
                } else {
                    Log.w(TAG, "Failed to download segment " + (i + 1) + ", skipping...");
                }
                
                // Small delay to avoid overwhelming the server
                Thread.sleep(100);
            }
            
        } finally {
            outputStream.close();
        }
        
        Log.d(TAG, "All segments combined into: " + outputFile.getAbsolutePath());
    }
    
    /**
     * Download a single video segment
     */
    private byte[] downloadSegment(String segmentUrl, Bundle headers) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(segmentUrl).openConnection();
            
            // Set headers based on segment URL
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            
            // Set appropriate referer based on segment URL
            if (segmentUrl.contains("breezeflash")) {
                connection.setRequestProperty("Referer", "https://videostr.net/");
                connection.setRequestProperty("Origin", "https://videostr.net");
            } else if (segmentUrl.contains("vidlink")) {
                connection.setRequestProperty("Referer", "https://vidlink.pro/");
                connection.setRequestProperty("Origin", "https://vidlink.pro");
            } else if (segmentUrl.contains("vidsrc")) {
                connection.setRequestProperty("Referer", "https://vidsrc.to/");
                connection.setRequestProperty("Origin", "https://vidsrc.to");
            } else {
                connection.setRequestProperty("Referer", "https://videostr.net/");
                connection.setRequestProperty("Origin", "https://videostr.net");
            }
            
            // Add custom headers if provided
            if (headers != null) {
                for (String key : headers.keySet()) {
                    String value = headers.getString(key);
                    if (value != null) {
                        connection.setRequestProperty(key, value);
                    }
                }
            }
            
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Log.w(TAG, "Segment download failed: " + responseCode + " for " + segmentUrl);
                return null;
            }
            
            // Read segment data
            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            inputStream.close();
            connection.disconnect();
            
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            Log.e(TAG, "Error downloading segment: " + e.getMessage());
            return null;
        }
    }
}