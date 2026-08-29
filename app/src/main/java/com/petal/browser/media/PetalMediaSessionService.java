package com.petal.browser.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;

/**
 * PetalMediaSessionService
 * Foreground MediaSessionService with notification controls (play, pause, skip, scrub)
 * that binds to WebView video elements or HTML5 audio tags to maintain uninterrupted audio
 * when the screen turns off or the app is minimized.
 */
public class PetalMediaSessionService extends Service {

    private static final String TAG = "PetalMediaSession";
    public static final String CHANNEL_ID = "petal_media_playback";
    public static final int NOTIFICATION_ID = 1002;

    public static final String ACTION_PLAY = "com.petal.browser.media.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.petal.browser.media.ACTION_PAUSE";
    public static final String ACTION_STOP = "com.petal.browser.media.ACTION_STOP";
    public static final String ACTION_SKIP_FORWARD = "com.petal.browser.media.ACTION_SKIP_FORWARD";
    public static final String ACTION_SKIP_BACKWARD = "com.petal.browser.media.ACTION_SKIP_BACKWARD";
    public static final String ACTION_SPEED_TOGGLE = "com.petal.browser.media.ACTION_SPEED_TOGGLE";
    public static final String ACTION_MUTE_TOGGLE = "com.petal.browser.media.ACTION_MUTE_TOGGLE";

    private final IBinder binder = new LocalBinder();
    private MediaSessionCompat mediaSession;
    private MediaSessionCallback mediaCallback;

    private boolean isPlaying = false;
    private String currentTitle = "Web Media";
    private String currentArtist = "Petal Browser";
    private long currentPosition = 0;
    private long currentDuration = 0;
    private float currentSpeed = 1.0f;
    private boolean isMuted = false;

    public interface MediaControlListener {
        void onPlay();
        void onPause();
        void onStop();
        void onSeekTo(long positionMs);
        default void onSpeedToggle(float newSpeed) {}
        default void onMuteToggle() {}
        default void onSkip(int deltaSeconds) {}
    }

    private MediaControlListener controlListener;

    public class LocalBinder extends Binder {
        public PetalMediaSessionService getService() {
            return PetalMediaSessionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, this, MediaButtonReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE);
        mediaSession.setMediaButtonReceiver(pendingIntent);

        mediaCallback = new MediaSessionCallback();
        mediaSession.setCallback(mediaCallback);
        mediaSession.setActive(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    if (controlListener != null) controlListener.onPlay();
                    updatePlaybackState(true, currentPosition);
                    break;
                case ACTION_PAUSE:
                    if (controlListener != null) controlListener.onPause();
                    updatePlaybackState(false, currentPosition);
                    break;
                case ACTION_STOP:
                    if (controlListener != null) controlListener.onStop();
                    stopForeground(true);
                    stopSelf();
                    break;
                case ACTION_SKIP_BACKWARD:
                    if (controlListener != null) controlListener.onSkip(-10);
                    break;
                case ACTION_SKIP_FORWARD:
                    if (controlListener != null) controlListener.onSkip(10);
                    break;
                case ACTION_SPEED_TOGGLE:
                    cycleSpeed();
                    if (controlListener != null) controlListener.onSpeedToggle(currentSpeed);
                    updateMediaState(currentTitle, currentArtist, isPlaying, currentPosition, currentDuration);
                    break;
                case ACTION_MUTE_TOGGLE:
                    isMuted = !isMuted;
                    if (controlListener != null) controlListener.onMuteToggle();
                    updateMediaState(currentTitle, currentArtist, isPlaying, currentPosition, currentDuration);
                    break;
            }
        }
        return START_NOT_STICKY;
    }

        private void cycleSpeed() {
        if (currentSpeed == 1.0f) currentSpeed = 1.25f;
        else if (currentSpeed == 1.25f) currentSpeed = 1.5f;
        else if (currentSpeed == 1.5f) currentSpeed = 2.0f;
        else currentSpeed = 1.0f;
    }

    public void setMediaControlListener(MediaControlListener listener) {
        this.controlListener = listener;
    }

    public void updateMediaState(String title, String artist, boolean playing, long positionMs, long durationMs) {
        this.currentTitle = title != null && !title.isEmpty() ? title : "Web Media";
        this.currentArtist = artist != null && !artist.isEmpty() ? artist : "Petal Browser";
        this.isPlaying = playing;
        this.currentPosition = positionMs;
        this.currentDuration = durationMs;

        updateMetadata();
        updatePlaybackState(playing, positionMs);

        Notification notification = buildNotification();
        if (playing) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private void updateMetadata() {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration);

        try {
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.icon_media_play);
            if (icon != null) {
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon);
            }
        } catch (Exception ignored) {}

        mediaSession.setMetadata(builder.build());
    }

    private void updatePlaybackState(boolean playing, long positionMs) {
        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long actions = PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_SEEK_TO |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, positionMs, 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, BrowserActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent playIntent = PendingIntent.getService(
                this, 1, new Intent(this, PetalMediaSessionService.class).setAction(ACTION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent pauseIntent = PendingIntent.getService(
                this, 2, new Intent(this, PetalMediaSessionService.class).setAction(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent stopIntent = PendingIntent.getService(
                this, 3, new Intent(this, PetalMediaSessionService.class).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        androidx.media.app.NotificationCompat.MediaStyle style = new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_media_play)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setContentIntent(openPendingIntent)
                .setDeleteIntent(stopIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(style)
                .setOngoing(isPlaying);

        PendingIntent speedIntent = PendingIntent.getService(
                this, 4, new Intent(this, PetalMediaSessionService.class).setAction(ACTION_SPEED_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent muteIntent = PendingIntent.getService(
                this, 5, new Intent(this, PetalMediaSessionService.class).setAction(ACTION_MUTE_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent skipBackIntent = PendingIntent.getService(
                this, 6, new Intent(this, PetalMediaSessionService.class).setAction(ACTION_SKIP_BACKWARD), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent skipForwardIntent = PendingIntent.getService(
                this, 7, new Intent(this, PetalMediaSessionService.class).setAction(ACTION_SKIP_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        builder.addAction(R.drawable.icon_close, "-10s", skipBackIntent);
        if (isPlaying) {
            builder.addAction(R.drawable.icon_close, "Pause", pauseIntent);
        } else {
            builder.addAction(R.drawable.icon_media_play, "Play", playIntent);
        }
        builder.addAction(R.drawable.icon_close, "+10s", skipForwardIntent);
        builder.addAction(R.drawable.icon_close, String.format(java.util.Locale.US, "%.2fx", currentSpeed), speedIntent);
        builder.addAction(R.drawable.icon_close, isMuted ? "Unmute" : "Mute", muteIntent);

        // Enable live progress updates & live activity / notification bar compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Background Media Playback",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Media controls for web audio and background video playback");
                channel.setSound(null, null);
                channel.enableVibration(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            if (controlListener != null) controlListener.onPlay();
            updatePlaybackState(true, currentPosition);
        }

        @Override
        public void onPause() {
            if (controlListener != null) controlListener.onPause();
            updatePlaybackState(false, currentPosition);
        }

        @Override
        public void onStop() {
            if (controlListener != null) controlListener.onStop();
            stopForeground(true);
            stopSelf();
        }

        @Override
        public void onSeekTo(long pos) {
            if (controlListener != null) controlListener.onSeekTo(pos);
            currentPosition = pos;
            updatePlaybackState(isPlaying, pos);
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }
}
