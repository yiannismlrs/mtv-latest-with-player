package com.mtv.streaming;

import android.os.AsyncTask;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts direct stream URLs from embed pages
 */
public class StreamExtractor {
    private static final String TAG = "StreamExtractor";
    
    public interface StreamCallback {
        void onStreamFound(String streamUrl, String mimeType);
        void onError(String error);
    }
    
    public static class StreamInfo {
        public String url;
        public String mimeType;
        public String quality;
        
        public StreamInfo(String url, String mimeType, String quality) {
            this.url = url;
            this.mimeType = mimeType;
            this.quality = quality;
        }
    }
    
    public static void extractStream(String embedUrl, StreamCallback callback) {
        new AsyncTask<String, Void, StreamInfo>() {
            private String errorMessage = null;
            
            @Override
            protected StreamInfo doInBackground(String... urls) {
                try {
                    String embedUrl = urls[0];
                    Log.d(TAG, "Extracting stream from: " + embedUrl);
                    
                    // Try multiple extraction methods
                    StreamInfo stream = tryVidsrcNetExtraction(embedUrl);
                    if (stream != null) return stream;
                    
                    stream = tryVidsrcToExtraction(embedUrl);
                    if (stream != null) return stream;
                    
                    stream = tryGenericExtraction(embedUrl);
                    if (stream != null) return stream;
                    
                    errorMessage = "No streams found in embed page";
                    return null;
                    
                } catch (Exception e) {
                    Log.e(TAG, "Stream extraction failed: " + e.getMessage());
                    errorMessage = "Extraction failed: " + e.getMessage();
                    return null;
                }
            }
            
            @Override
            protected void onPostExecute(StreamInfo result) {
                if (result != null) {
                    Log.d(TAG, "✓ Stream extracted: " + result.url);
                    callback.onStreamFound(result.url, result.mimeType);
                } else {
                    Log.e(TAG, "✗ Stream extraction failed: " + errorMessage);
                    callback.onError(errorMessage != null ? errorMessage : "Unknown error");
                }
            }
        }.execute(embedUrl);
    }
    
    private static StreamInfo tryVidsrcNetExtraction(String embedUrl) {
        try {
            Log.d(TAG, "Trying vidsrc.net extraction...");
            
            // Convert embed URL to API URL
            String apiUrl = convertToApiUrl(embedUrl);
            if (apiUrl == null) return null;
            
            Log.d(TAG, "API URL: " + apiUrl);
            
            String response = fetchUrl(apiUrl, true);
            if (response == null) return null;
            
            // Try to parse JSON response
            StreamInfo stream = parseApiResponse(response);
            if (stream != null) {
                Log.d(TAG, "✓ vidsrc.net API extraction successful");
                return stream;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "vidsrc.net extraction failed: " + e.getMessage());
        }
        return null;
    }
    
    private static StreamInfo tryVidsrcToExtraction(String embedUrl) {
        try {
            Log.d(TAG, "Trying vidsrc.to extraction...");
            
            // Convert to vidsrc.to format
            String vidsrcToUrl = embedUrl.replace("vidsrc.net", "vidsrc.to")
                                         .replace("/embed/movie?tmdb=", "/embed/movie/")
                                         .replace("/embed/tv?tmdb=", "/embed/tv/")
                                         .replaceAll("&season=", "/")
                                         .replaceAll("&episode=", "/");
            
            Log.d(TAG, "Vidsrc.to URL: " + vidsrcToUrl);
            
            String html = fetchUrl(vidsrcToUrl, false);
            if (html == null) return null;
            
            StreamInfo stream = extractFromHtml(html);
            if (stream != null) {
                Log.d(TAG, "✓ vidsrc.to extraction successful");
                return stream;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "vidsrc.to extraction failed: " + e.getMessage());
        }
        return null;
    }
    
    private static StreamInfo tryGenericExtraction(String embedUrl) {
        try {
            Log.d(TAG, "Trying generic HTML extraction...");
            
            String html = fetchUrl(embedUrl, false);
            if (html == null) return null;
            
            StreamInfo stream = extractFromHtml(html);
            if (stream != null) {
                Log.d(TAG, "✓ Generic extraction successful");
                return stream;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Generic extraction failed: " + e.getMessage());
        }
        return null;
    }
    
    private static String convertToApiUrl(String embedUrl) {
        try {
            if (embedUrl.contains("vidsrc.net/embed/movie?tmdb=")) {
                String tmdbId = embedUrl.split("tmdb=")[1].split("&")[0];
                return "https://vidsrc.net/api/source/" + tmdbId;
            } else if (embedUrl.contains("vidsrc.net/embed/tv?tmdb=")) {
                String[] parts = embedUrl.split("tmdb=")[1].split("&");
                String tmdbId = parts[0];
                return "https://vidsrc.net/api/source/" + tmdbId;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to convert to API URL: " + e.getMessage());
        }
        return null;
    }
    
    private static StreamInfo parseApiResponse(String response) {
        try {
            // Simple JSON parsing for common patterns
            if (response.contains("\"url\"")) {
                Pattern urlPattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = urlPattern.matcher(response);
                if (matcher.find()) {
                    String url = matcher.group(1);
                    String mimeType = url.contains(".m3u8") ? "application/x-mpegURL" : "video/mp4";
                    return new StreamInfo(url, mimeType, "auto");
                }
            }
            
            if (response.contains("\"file\"")) {
                Pattern filePattern = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = filePattern.matcher(response);
                if (matcher.find()) {
                    String url = matcher.group(1);
                    String mimeType = url.contains(".m3u8") ? "application/x-mpegURL" : "video/mp4";
                    return new StreamInfo(url, mimeType, "auto");
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse API response: " + e.getMessage());
        }
        return null;
    }
    
    private static StreamInfo extractFromHtml(String html) {
        List<String> patterns = new ArrayList<>();
        
        // HLS patterns
        patterns.add("(https?://[^\\s\"']+\\.m3u8(?:\\?[^\\s\"']*)?)");;
        patterns.add("(?:source|src|url|file)\\s*[:=]\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']");
        
        // MP4 patterns  
        patterns.add("(https?://[^\\s\"']+\\.mp4(?:\\?[^\\s\"']*)?)");;
        patterns.add("(?:source|src|url|file)\\s*[:=]\\s*[\"']([^\"']+\\.mp4[^\"']*)[\"']");
        
        for (String patternStr : patterns) {
            try {
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(html);
                
                while (matcher.find()) {
                    String url = matcher.group(1);
                    if (url.startsWith("http")) {
                        String mimeType = url.contains(".m3u8") ? "application/x-mpegURL" : "video/mp4";
                        Log.d(TAG, "Found stream URL: " + url);
                        return new StreamInfo(url, mimeType, "auto");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Pattern matching failed: " + e.getMessage());
            }
        }
        
        return null;
    }
    
    private static String fetchUrl(String urlString, boolean isApi) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Set headers
            if (isApi) {
                connection.setRequestProperty("Accept", "application/json, text/plain, */*");
                connection.setRequestProperty("Content-Type", "application/json");
            } else {
                connection.setRequestProperty("Accept", 
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            }
            
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Referer", "https://vidsrc.net/");
            
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP response: " + responseCode + " for " + urlString);
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                reader.close();
                connection.disconnect();
                
                String content = response.toString();
                Log.d(TAG, "Fetched content length: " + content.length());
                return content;
            } else {
                Log.w(TAG, "HTTP error: " + responseCode);
            }
            
            connection.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch URL: " + e.getMessage());
        }
        return null;
    }
}