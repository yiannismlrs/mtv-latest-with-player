package com.mtv.streaming.download;

import android.util.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

/**
 * VidLink stream extractor for downloads
 * Extracts direct video URLs from VidLink embed pages
 */
public class VidLinkExtractor {
    private static final String TAG = "VidLinkExtractor";
    
    public static class ExtractedStream {
        public final String url;
        public final String quality;
        public final String type; // "mp4", "m3u8"
        public final Map<String, String> headers;
        public final long sizeBytes;
        
        public ExtractedStream(String url, String quality, String type, Map<String, String> headers, long sizeBytes) {
            this.url = url;
            this.quality = quality;
            this.type = type;
            this.headers = headers;
            this.sizeBytes = sizeBytes;
        }
        
        public String getFileName(String title, String season, String episode) {
            String cleanTitle = title.replaceAll("[^a-zA-Z0-9\\s]", "").trim().replaceAll("\\s+", "_");
            String filename;
            
            if (season != null && episode != null) {
                filename = String.format("%s_S%sE%s_%s.%s", 
                    cleanTitle, season, episode, quality, getFileExtension());
            } else {
                filename = String.format("%s_%s.%s", 
                    cleanTitle, quality, getFileExtension());
            }
            
            return filename;
        }
        
        private String getFileExtension() {
            return type.equals("m3u8") ? "mp4" : type;
        }
    }
    
    public static class ExtractionResult {
        public final boolean success;
        public final List<ExtractedStream> streams;
        public final String error;
        
        public ExtractionResult(List<ExtractedStream> streams) {
            this.success = streams != null && !streams.isEmpty();
            this.streams = streams != null ? streams : new ArrayList<>();
            this.error = null;
        }
        
        public ExtractionResult(String error) {
            this.success = false;
            this.streams = new ArrayList<>();
            this.error = error;
        }
        
        public ExtractedStream getBestStream() {
            if (streams.isEmpty()) return null;
            
            // Prefer MP4 over M3U8 for downloads, then by quality
            return streams.stream()
                .sorted((a, b) -> {
                    // Prefer MP4
                    if (a.type.equals("mp4") && !b.type.equals("mp4")) return -1;
                    if (!a.type.equals("mp4") && b.type.equals("mp4")) return 1;
                    
                    // Then by quality
                    return compareQuality(b.quality, a.quality);
                })
                .findFirst()
                .orElse(streams.get(0));
        }
        
        private int compareQuality(String q1, String q2) {
            return Integer.compare(getQualityValue(q1), getQualityValue(q2));
        }
        
        private int getQualityValue(String quality) {
            if (quality == null) return 0;
            if (quality.contains("1080")) return 1080;
            if (quality.contains("720")) return 720;
            if (quality.contains("480")) return 480;
            if (quality.contains("360")) return 360;
            return 0;
        }
    }
    
    public static CompletableFuture<ExtractionResult> extractDownloadableStreams(String vidlinkUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Extracting streams from: " + vidlinkUrl);
                
                // Fetch the VidLink page
                Document doc = Jsoup.connect(vidlinkUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .referrer("https://vidlink.pro/")
                    .timeout(15000)
                    .get();
                
                List<ExtractedStream> streams = new ArrayList<>();
                
                // Method 1: Look for video sources in script tags
                Elements scripts = doc.select("script");
                for (Element script : scripts) {
                    String content = script.html();
                    
                    // Look for various video URL patterns
                    streams.addAll(extractFromScript(content));
                }
                
                // Method 2: Look for video elements
                Elements videoElements = doc.select("video source, video");
                for (Element video : videoElements) {
                    String src = video.attr("src");
                    if (!src.isEmpty() && (src.contains(".mp4") || src.contains(".m3u8"))) {
                        streams.add(createStreamFromUrl(src));
                    }
                }
                
                // Method 3: Look for iframe sources (common in embed players)
                Elements iframes = doc.select("iframe");
                for (Element iframe : iframes) {
                    String src = iframe.attr("src");
                    if (!src.isEmpty() && !src.contains("vidlink.pro")) {
                        // Extract from nested iframe
                        streams.addAll(extractFromNested(src));
                    }
                }
                
                Log.d(TAG, "Found " + streams.size() + " streams");
                
                if (streams.isEmpty()) {
                    return new ExtractionResult("No downloadable streams found");
                }
                
                return new ExtractionResult(streams);
                
            } catch (IOException e) {
                Log.e(TAG, "Network error extracting streams: " + e.getMessage());
                return new ExtractionResult("Network error: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error extracting streams: " + e.getMessage());
                return new ExtractionResult("Extraction error: " + e.getMessage());
            }
        });
    }
    
    private static List<ExtractedStream> extractFromScript(String scriptContent) {
        List<ExtractedStream> streams = new ArrayList<>();
        
        // Common patterns for video URLs in JavaScript
        Pattern[] patterns = {
            Pattern.compile("\"(https?://[^\"]+\\.mp4[^\"]*?)\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"(https?://[^\"]+\\.m3u8[^\"]*?)\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("'(https?://[^']+\\.mp4[^']*?)'", Pattern.CASE_INSENSITIVE),
            Pattern.compile("'(https?://[^']+\\.m3u8[^']*?)'", Pattern.CASE_INSENSITIVE),
            Pattern.compile("file\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("src\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
        };
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(scriptContent);
            while (matcher.find()) {
                String url = matcher.group(1);
                if (isValidVideoUrl(url)) {
                    streams.add(createStreamFromUrl(url));
                }
            }
        }
        
        return streams;
    }
    
    private static List<ExtractedStream> extractFromNested(String iframeUrl) {
        List<ExtractedStream> streams = new ArrayList<>();
        
        try {
            Document doc = Jsoup.connect(iframeUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .referrer("https://vidlink.pro/")
                .timeout(10000)
                .get();
            
            Elements scripts = doc.select("script");
            for (Element script : scripts) {
                streams.addAll(extractFromScript(script.html()));
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract from nested iframe: " + e.getMessage());
        }
        
        return streams;
    }
    
    private static boolean isValidVideoUrl(String url) {
        return url != null && 
               url.startsWith("http") && 
               (url.contains(".mp4") || url.contains(".m3u8")) &&
               !url.contains("subtitle") &&
               !url.contains("thumbnail");
    }
    
    private static ExtractedStream createStreamFromUrl(String url) {
        String quality = extractQuality(url);
        String type = url.contains(".m3u8") ? "m3u8" : "mp4";
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://vidlink.pro/");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        
        return new ExtractedStream(url, quality, type, headers, 0); // Size will be determined during download
    }
    
    private static String extractQuality(String url) {
        if (url.contains("1080")) return "1080p";
        if (url.contains("720")) return "720p";
        if (url.contains("480")) return "480p";
        if (url.contains("360")) return "360p";
        
        // Default quality
        return "720p";
    }
}
