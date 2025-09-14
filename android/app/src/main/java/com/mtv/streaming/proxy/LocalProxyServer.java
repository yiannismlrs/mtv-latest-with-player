package com.mtv.streaming.proxy;

import android.os.Bundle;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local HTTP proxy server for handling streams with headers
 * Used when SPlayer ignores headers on direct stream URLs
 */
public class LocalProxyServer {
    private static final String TAG = "LocalProxyServer";
    private static final int PROXY_PORT = 8888;
    private static LocalProxyServer instance;
    
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private AtomicBoolean isRunning;
    private String originalStreamUrl;
    private Bundle streamHeaders;
    
    private LocalProxyServer() {
        this.executor = Executors.newCachedThreadPool();
        this.isRunning = new AtomicBoolean(false);
    }
    
    public static synchronized LocalProxyServer getInstance() {
        if (instance == null) {
            instance = new LocalProxyServer();
        }
        return instance;
    }
    
    /**
     * Start proxy server and return local URL for SPlayer
     */
    public String startProxyForStream(String originalUrl, Bundle headers) {
        try {
            if (isRunning.get()) {
                stopProxy();
            }
            
            this.originalStreamUrl = originalUrl;
            this.streamHeaders = headers;
            
            serverSocket = new ServerSocket(PROXY_PORT);
            isRunning.set(true);
            
            Log.d(TAG, "Starting proxy server on port " + PROXY_PORT);
            Log.d(TAG, "Proxying URL: " + originalUrl);
            
            // Start server thread
            executor.submit(this::runProxyServer);
            
            // Return local URL that SPlayer can use
            String localUrl = "http://127.0.0.1:" + PROXY_PORT + "/master.m3u8";
            Log.d(TAG, "Local proxy URL: " + localUrl);
            
            return localUrl;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start proxy server: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Stop proxy server
     */
    public void stopProxy() {
        isRunning.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                Log.d(TAG, "Proxy server stopped");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error stopping proxy: " + e.getMessage());
        }
    }
    
    private void runProxyServer() {
        Log.d(TAG, "Proxy server started, waiting for connections...");
        
        while (isRunning.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            } catch (Exception e) {
                if (isRunning.get()) {
                    Log.w(TAG, "Error accepting client: " + e.getMessage());
                }
            }
        }
    }
    
    private void handleClient(Socket clientSocket) {
        try {
            Log.d(TAG, "Handling client connection");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream outputStream = clientSocket.getOutputStream();
            
            // Read HTTP request
            String requestLine = reader.readLine();
            if (requestLine == null) {
                clientSocket.close();
                return;
            }
            
            Log.d(TAG, "Request: " + requestLine);
            
            // Skip headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Skip client headers
            }
            
            // Determine what to proxy
            if (requestLine.contains("/master.m3u8")) {
                // Proxy the original stream URL
                proxyStream(outputStream, originalStreamUrl);
            } else {
                // Handle 404 for other requests
                send404(outputStream);
            }
            
            clientSocket.close();
            
        } catch (Exception e) {
            Log.w(TAG, "Error handling client: " + e.getMessage());
        }
    }
    
    private void proxyStream(OutputStream clientOutput, String streamUrl) {
        try {
            Log.d(TAG, "Proxying stream: " + streamUrl);
            
            // Create connection to original stream with headers
            HttpURLConnection connection = (HttpURLConnection) new URL(streamUrl).openConnection();
            
            // Add original headers
            if (streamHeaders != null) {
                for (String key : streamHeaders.keySet()) {
                    String value = streamHeaders.getString(key);
                    if (value != null) {
                        connection.setRequestProperty(key, value);
                        Log.d(TAG, "Adding header: " + key + " = " + value);
                    }
                }
            }
            
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Original stream response code: " + responseCode);
            
            if (responseCode == 200) {
                // Send HTTP response headers to client
                PrintWriter writer = new PrintWriter(clientOutput);
                writer.println("HTTP/1.1 200 OK");
                writer.println("Content-Type: " + getContentType(streamUrl));
                writer.println("Access-Control-Allow-Origin: *");
                writer.println("Access-Control-Allow-Headers: *");
                writer.println("Connection: close");
                writer.println(); // Empty line to end headers
                writer.flush();
                
                // Stream content to client
                InputStream inputStream = connection.getInputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    clientOutput.write(buffer, 0, bytesRead);
                    clientOutput.flush();
                }
                
                inputStream.close();
                Log.d(TAG, "Successfully proxied stream content");
                
            } else {
                Log.w(TAG, "Failed to fetch original stream: " + responseCode);
                send404(clientOutput);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error proxying stream: " + e.getMessage());
            try {
                send404(clientOutput);
            } catch (Exception ex) {
                Log.w(TAG, "Error sending 404: " + ex.getMessage());
            }
        }
    }
    
    private void send404(OutputStream output) throws IOException {
        PrintWriter writer = new PrintWriter(output);
        writer.println("HTTP/1.1 404 Not Found");
        writer.println("Content-Type: text/plain");
        writer.println("Connection: close");
        writer.println();
        writer.println("404 Not Found");
        writer.flush();
    }
    
    private String getContentType(String url) {
        if (url.contains(".m3u8")) {
            return "application/x-mpegURL";
        } else if (url.contains(".mp4")) {
            return "video/mp4";
        } else {
            return "application/octet-stream";
        }
    }
    
    /**
     * Check if proxy is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Get proxy port
     */
    public int getProxyPort() {
        return PROXY_PORT;
    }
}
