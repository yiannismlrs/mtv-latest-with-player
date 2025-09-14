package com.mtv.streaming;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import java.util.HashMap;
import java.util.Map;

public class VideoPlayerActivity extends Activity {
    private static final String TAG = "VideoPlayerActivity";
    
    // Intent extras
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_HEADERS = "headers";
    
    // Player components
    private ExoPlayer player;
    private PlayerView playerView;
    
    // Custom controls
    private ConstraintLayout controlsContainer;
    private ImageButton playPauseButton;
    private ImageButton rewindButton;
    private ImageButton fastForwardButton;
    private ImageButton speedButton;
    private ImageButton fullscreenButton;
    private ImageButton backButton;
    private SeekBar progressBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView titleText;
    private TextView speedText;
    
    // State variables
    private boolean isFullscreen = false;
    private boolean controlsVisible = true;
    private float currentSpeed = 1.0f;
    private final float[] speedOptions = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private int currentSpeedIndex = 2; // 1.0x
    
    // Auto-hide handler
    private Handler hideHandler = new Handler(Looper.getMainLooper());
    private Runnable hideRunnable = this::hideControls;
    private static final int HIDE_DELAY = 3000; // 3 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        
        // Keep screen on during playback
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Get intent data
        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        String videoTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        Bundle headers = getIntent().getBundleExtra(EXTRA_HEADERS);
        
        if (videoUrl == null) {
            Log.e(TAG, "No video URL provided");
            finish();
            return;
        }
        
        Log.d(TAG, "Playing video: " + videoTitle);
        Log.d(TAG, "URL: " + videoUrl);
        
        initializeViews();
        setupPlayer(videoUrl, headers);
        setupControls();
        
        if (videoTitle != null) {
            titleText.setText(videoTitle);
        }
        
        // Start auto-hide timer
        scheduleHideControls();
    }
    
    private void initializeViews() {
        playerView = findViewById(R.id.player_view);
        controlsContainer = findViewById(R.id.controls_container);
        playPauseButton = findViewById(R.id.play_pause_button);
        rewindButton = findViewById(R.id.rewind_button);
        fastForwardButton = findViewById(R.id.fast_forward_button);
        speedButton = findViewById(R.id.speed_button);
        fullscreenButton = findViewById(R.id.fullscreen_button);
        backButton = findViewById(R.id.back_button);
        progressBar = findViewById(R.id.progress_bar);
        currentTimeText = findViewById(R.id.current_time);
        totalTimeText = findViewById(R.id.total_time);
        titleText = findViewById(R.id.video_title);
        speedText = findViewById(R.id.speed_text);
        
        // Hide ExoPlayer's default controls
        playerView.setUseController(false);
        
        // Set up click listener to show/hide controls
        playerView.setOnClickListener(v -> toggleControlsVisibility());
    }
    
    private void setupPlayer(String videoUrl, Bundle headers) {
        // Create ExoPlayer instance
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        
        // Create data source factory with headers
        DataSource.Factory dataSourceFactory = createDataSourceFactory(headers);
        
        // Create media source based on URL type
        MediaSource mediaSource = createMediaSource(videoUrl, dataSourceFactory);
        
        // Set up player listener
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updatePlayPauseButton();
                if (playbackState == Player.STATE_READY) {
                    updateProgressBar();
                    updateTimeTexts();
                }
            }
            
            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage());
                Toast.makeText(VideoPlayerActivity.this, "Playback error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton();
                if (isPlaying) {
                    scheduleHideControls();
                } else {
                    cancelHideControls();
                }
            }
        });
        
        // Prepare and start playback
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }
    
    private DataSource.Factory createDataSourceFactory(Bundle headers) {
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(this, "MTVPlayer"))
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true);
        
        // Add custom headers if provided
        if (headers != null) {
            Map<String, String> headerMap = new HashMap<>();
            for (String key : headers.keySet()) {
                String value = headers.getString(key);
                if (value != null) {
                    headerMap.put(key, value);
                    Log.d(TAG, "Adding header: " + key + " = " + value);
                }
            }
            if (!headerMap.isEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(headerMap);
            }
        }
        
        return new DefaultDataSource.Factory(this, httpDataSourceFactory);
    }
    
    private MediaSource createMediaSource(String videoUrl, DataSource.Factory dataSourceFactory) {
        Uri uri = Uri.parse(videoUrl);
        
        if (videoUrl.contains(".m3u8")) {
            // HLS stream
            Log.d(TAG, "Creating HLS media source");
            return new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
        } else {
            // Progressive download (MP4, etc.)
            Log.d(TAG, "Creating progressive media source");
            return new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));
        }
    }
    
    private void setupControls() {
        // Play/Pause button
        playPauseButton.setOnClickListener(v -> {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            scheduleHideControls();
        });
        
        // Rewind button (-10 seconds)
        rewindButton.setOnClickListener(v -> {
            long currentPosition = player.getCurrentPosition();
            player.seekTo(Math.max(0, currentPosition - 10000));
            scheduleHideControls();
        });
        
        // Fast forward button (+10 seconds)
        fastForwardButton.setOnClickListener(v -> {
            long currentPosition = player.getCurrentPosition();
            long duration = player.getDuration();
            player.seekTo(Math.min(duration, currentPosition + 10000));
            scheduleHideControls();
        });
        
        // Speed control button
        speedButton.setOnClickListener(v -> {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.length;
            currentSpeed = speedOptions[currentSpeedIndex];
            player.setPlaybackSpeed(currentSpeed);
            speedText.setText(String.format("%.2fx", currentSpeed));
            scheduleHideControls();
        });
        
        // Fullscreen toggle
        fullscreenButton.setOnClickListener(v -> {
            toggleFullscreen();
            scheduleHideControls();
        });
        
        // Back button
        backButton.setOnClickListener(v -> finish());
        
        // Progress bar
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    long duration = player.getDuration();
                    long position = (duration * progress) / 100;
                    player.seekTo(position);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                cancelHideControls();
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scheduleHideControls();
            }
        });
        
        // Update progress periodically
        Handler progressHandler = new Handler(Looper.getMainLooper());
        Runnable progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null) {
                    updateProgressBar();
                    updateTimeTexts();
                }
                progressHandler.postDelayed(this, 1000);
            }
        };
        progressHandler.post(progressRunnable);
        
        // Initialize speed text
        speedText.setText("1.00x");
    }
    
    private void updatePlayPauseButton() {
        if (player != null && player.isPlaying()) {
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }
    
    private void updateProgressBar() {
        if (player != null) {
            long duration = player.getDuration();
            long position = player.getCurrentPosition();
            
            if (duration > 0) {
                int progress = (int) ((position * 100) / duration);
                progressBar.setProgress(progress);
            }
        }
    }
    
    private void updateTimeTexts() {
        if (player != null) {
            long duration = player.getDuration();
            long position = player.getCurrentPosition();
            
            currentTimeText.setText(formatTime(position));
            totalTimeText.setText(formatTime(duration));
        }
    }
    
    private String formatTime(long timeMs) {
        if (timeMs <= 0) return "00:00";
        
        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
    
    private void toggleControlsVisibility() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }
    
    private void showControls() {
        controlsContainer.setVisibility(View.VISIBLE);
        controlsVisible = true;
        scheduleHideControls();
    }
    
    private void hideControls() {
        controlsContainer.setVisibility(View.GONE);
        controlsVisible = false;
        cancelHideControls();
    }
    
    private void scheduleHideControls() {
        cancelHideControls();
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY);
    }
    
    private void cancelHideControls() {
        hideHandler.removeCallbacks(hideRunnable);
    }
    
    private void toggleFullscreen() {
        if (isFullscreen) {
            // Exit fullscreen
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            fullscreenButton.setImageResource(android.R.drawable.ic_menu_crop);
        } else {
            // Enter fullscreen
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            fullscreenButton.setImageResource(android.R.drawable.ic_menu_revert);
        }
        isFullscreen = !isFullscreen;
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handle orientation changes
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
        }
        cancelHideControls();
    }
    
    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen();
        } else {
            super.onBackPressed();
        }
    }
}