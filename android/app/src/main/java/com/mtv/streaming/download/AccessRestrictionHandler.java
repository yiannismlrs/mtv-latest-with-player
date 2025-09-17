package com.mtv.streaming.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles access restrictions for streaming sources
 * Provides fallback methods when primary sources are blocked or restricted
 */
public class AccessRestrictionHandler {
    private static final String TAG = "AccessRestrictionHandler";
    private final Context context;
    
    public enum RestrictionType {
        GEO_BLOCKED,
        RATE_LIMITED,
        ACCOUNT_REQUIRED,
        CAPTCHA_REQUIRED,
        IP_BLOCKED,
        SERVER_DOWN,
        UNKNOWN_ERROR
    }
    
    public static class RestrictionInfo {
        public final RestrictionType type;
        public final String message;
        public final String suggestedAction;
        public final List<String> alternatives;
        
        public RestrictionInfo(RestrictionType type, String message, String suggestedAction, List<String> alternatives) {
            this.type = type;
            this.message = message;
            this.suggestedAction = suggestedAction;
            this.alternatives = alternatives != null ? alternatives : new ArrayList<>();
        }
    }
    
    public AccessRestrictionHandler(Context context) {
        this.context = context;
    }
    
    /**
     * Test if a URL is accessible and detect restriction type
     */
    public CompletableFuture<RestrictionInfo> detectRestrictions(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Testing access to: " + url);
                
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                
                // Set realistic headers to avoid basic bot detection
                connection.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
                connection.setRequestProperty("DNT", "1");
                connection.setRequestProperty("Connection", "keep-alive");
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
                
                // Set appropriate referer based on URL
                if (url.contains("vidlink.pro")) {
                    connection.setRequestProperty("Referer", "https://vidlink.pro/");
                    connection.setRequestProperty("Origin", "https://vidlink.pro");
                } else if (url.contains("vidsrc")) {
                    connection.setRequestProperty("Referer", "https://vidsrc.to/");
                    connection.setRequestProperty("Origin", "https://vidsrc.to");
                }
                
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                
                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();
                
                Log.d(TAG, "Response: " + responseCode + " - " + responseMessage);
                
                // Analyze response to determine restriction type
                RestrictionInfo restriction = analyzeResponse(responseCode, responseMessage, connection, url);
                
                connection.disconnect();
                return restriction;
                
            } catch (Exception e) {
                Log.w(TAG, "Error testing URL: " + e.getMessage());
                
                if (e.getMessage().contains("timeout")) {
                    return new RestrictionInfo(RestrictionType.SERVER_DOWN, 
                        "Server is not responding", 
                        "Try alternative sources",
                        generateAlternatives(url));
                } else if (e.getMessage().contains("blocked") || e.getMessage().contains("refused")) {
                    return new RestrictionInfo(RestrictionType.IP_BLOCKED, 
                        "Access blocked from your location", 
                        "Use VPN or try alternative sources",
                        generateAlternatives(url));
                } else {
                    return new RestrictionInfo(RestrictionType.UNKNOWN_ERROR, 
                        "Unknown access error: " + e.getMessage(), 
                        "Try alternative sources",
                        generateAlternatives(url));
                }
            }
        });
    }
    
    /**
     * Analyze HTTP response to determine restriction type
     */
    private RestrictionInfo analyzeResponse(int responseCode, String responseMessage, 
                                          HttpURLConnection connection, String url) {
        
        List<String> alternatives = generateAlternatives(url);
        
        switch (responseCode) {
            case 200:
                // Check for geo-blocking messages in content
                try {
                    String content = readResponseContent(connection);
                    if (content.toLowerCase().contains("geo") && 
                        (content.toLowerCase().contains("block") || content.toLowerCase().contains("restrict"))) {
                        return new RestrictionInfo(RestrictionType.GEO_BLOCKED,
                            "Content geo-blocked in your region",
                            "Use VPN or try alternative sources", alternatives);
                    }
                    
                    if (content.toLowerCase().contains("captcha")) {
                        return new RestrictionInfo(RestrictionType.CAPTCHA_REQUIRED,
                            "Site requires captcha verification",
                            "Try alternative sources", alternatives);
                    }
                    
                    // Success case
                    return new RestrictionInfo(null, "Access available", "Proceed with download", new ArrayList<>());
                    
                } catch (Exception e) {
                    Log.w(TAG, "Error reading response content: " + e.getMessage());
                }
                break;
                
            case 403:
                return new RestrictionInfo(RestrictionType.GEO_BLOCKED,
                    "Access forbidden - possibly geo-blocked",
                    "Use VPN or try alternative sources", alternatives);
                    
            case 429:
                return new RestrictionInfo(RestrictionType.RATE_LIMITED,
                    "Rate limit exceeded - too many requests",
                    "Wait a few minutes or try alternative sources", alternatives);
                    
            case 401:
                return new RestrictionInfo(RestrictionType.ACCOUNT_REQUIRED,
                    "Authentication required",
                    "Try alternative sources", alternatives);
                    
            case 404:
                return new RestrictionInfo(RestrictionType.SERVER_DOWN,
                    "Content not found or server down",
                    "Try alternative sources", alternatives);
                    
            case 503:
            case 502:
            case 500:
                return new RestrictionInfo(RestrictionType.SERVER_DOWN,
                    "Server temporarily unavailable",
                    "Wait and retry, or try alternative sources", alternatives);
                    
            default:
                return new RestrictionInfo(RestrictionType.UNKNOWN_ERROR,
                    "HTTP Error: " + responseCode + " - " + responseMessage,
                    "Try alternative sources", alternatives);
        }
        
        return new RestrictionInfo(null, "Access available", "Proceed with download", new ArrayList<>());
    }
    
    /**
     * Read response content to analyze for restriction messages
     */
    private String readResponseContent(HttpURLConnection connection) throws Exception {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream()));
        
        String line;
        int lines = 0;
        while ((line = reader.readLine()) != null && lines < 50) { // Limit reading to avoid huge responses
            content.append(line.toLowerCase()).append("\n");
            lines++;
        }
        reader.close();
        
        return content.toString();
    }
    
    /**
     * Generate alternative sources based on the original URL
     */
    private List<String> generateAlternatives(String originalUrl) {
        List<String> alternatives = new ArrayList<>();
        
        // Extract TMDB ID if possible
        String tmdbId = extractTmdbId(originalUrl);
        if (tmdbId == null) return alternatives;
        
        // Determine if it's movie or TV
        boolean isMovie = originalUrl.contains("/movie/");
        
        if (isMovie) {
            alternatives.add("https://vidsrc.to/embed/movie/" + tmdbId);
            alternatives.add("https://vidsrc.cc/v2/embed/movie/" + tmdbId);
            alternatives.add("https://2embed.to/embed/tmdb/movie?id=" + tmdbId);
            alternatives.add("https://www.2embed.cc/embed/tmdb/movie?id=" + tmdbId);
            alternatives.add("https://embed.su/embed/movie/" + tmdbId);
        } else if (originalUrl.contains("/tv/")) {
            // Extract season and episode
            String[] parts = originalUrl.split("/");
            if (parts.length >= 6) {
                String season = parts[parts.length - 2];
                String episode = parts[parts.length - 1];
                
                alternatives.add("https://vidsrc.to/embed/tv/" + tmdbId + "/" + season + "/" + episode);
                alternatives.add("https://vidsrc.cc/v2/embed/tv/" + tmdbId + "/" + season + "/" + episode);
                alternatives.add("https://2embed.to/embed/tmdb/tv?id=" + tmdbId + "&s=" + season + "&e=" + episode);
                alternatives.add("https://www.2embed.cc/embed/tmdb/tv?id=" + tmdbId + "&s=" + season + "&e=" + episode);
                alternatives.add("https://embed.su/embed/tv/" + tmdbId + "/" + season + "/" + episode);
            }
        }
        
        return alternatives;
    }
    
    /**
     * Extract TMDB ID from various URL formats
     */
    private String extractTmdbId(String url) {
        try {
            if (url.contains("vidlink.pro/movie/")) {
                return url.substring(url.lastIndexOf("/") + 1).split("\\?")[0];
            } else if (url.contains("vidlink.pro/tv/")) {
                String[] parts = url.split("/");
                if (parts.length >= 5) {
                    return parts[4]; // Get TMDB ID from /tv/{id}/{season}/{episode}
                }
            } else if (url.contains("tmdb")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:id=|tmdb=)(\\\\d+)");
                java.util.regex.Matcher matcher = pattern.matcher(url);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting TMDB ID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Provide user-friendly guidance for restriction workarounds
     */
    public String getRestrictionGuidance(RestrictionType type) {
        switch (type) {
            case GEO_BLOCKED:
                return "Content is blocked in your region. Recommendations:\n" +
                       "• Use a VPN service (NordVPN, ExpressVPN, etc.)\n" +
                       "• Try alternative streaming sources\n" +
                       "• Check if content is available on local platforms";
                       
            case RATE_LIMITED:
                return "Too many requests detected. Recommendations:\n" +
                       "• Wait 10-15 minutes before trying again\n" +
                       "• Try alternative sources\n" +
                       "• Use mobile data instead of Wi-Fi (different IP)";
                       
            case CAPTCHA_REQUIRED:
                return "Site requires human verification. Recommendations:\n" +
                       "• Open the site manually in browser first\n" +
                       "• Try alternative sources\n" +
                       "• Use different network connection";
                       
            case IP_BLOCKED:
                return "Your IP address is blocked. Recommendations:\n" +
                       "• Use VPN or proxy service\n" +
                       "• Switch to mobile data or different Wi-Fi\n" +
                       "• Try alternative sources";
                       
            case SERVER_DOWN:
                return "Server is temporarily unavailable. Recommendations:\n" +
                       "• Try again in a few minutes\n" +
                       "• Check alternative sources\n" +
                       "• Verify your internet connection";
                       
            default:
                return "Access restricted. Try alternative sources or check your connection.";
        }
    }
    
    /**
     * Open VPN setup guidance for users
     */
    public void openVPNGuidance() {
        try {
            // Open Play Store search for VPN apps
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://search?q=vpn free"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback to web search
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/search?q=vpn%20free"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e2) {
                Log.w(TAG, "Could not open VPN guidance");
            }
        }
    }
    
    /**
     * Check if alternative downloaders are available
     */
    public List<String> getAvailableDownloaders() {
        List<String> downloaders = new ArrayList<>();
        
        // Check for popular download apps
        String[] downloadApps = {
            "com.dv.adm", // ADM
            "idm.internet.download.manager", // IDM+
            "com.speedsoftware.explorer", // Speed Download Manager
            "com.tachibana.downloader", // Turbo Download Manager
            "com.dv.adm.pay" // ADM Pro
        };
        
        for (String packageName : downloadApps) {
            try {
                context.getPackageManager().getPackageInfo(packageName, 0);
                downloaders.add(packageName);
            } catch (Exception e) {
                // App not installed
            }
        }
        
        return downloaders;
    }
    
    /**
     * Open external downloader with URL
     */
    public boolean openExternalDownloader(String url) {
        List<String> availableDownloaders = getAvailableDownloaders();
        
        if (availableDownloaders.isEmpty()) {
            // No external downloaders found - suggest installation
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://search?q=download manager"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        try {
            // Use the first available downloader
            String packageName = availableDownloaders.get(0);
            
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.setPackage(packageName);
            intent.putExtra(Intent.EXTRA_TEXT, url);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(intent);
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to open external downloader: " + e.getMessage());
            return false;
        }
    }
}