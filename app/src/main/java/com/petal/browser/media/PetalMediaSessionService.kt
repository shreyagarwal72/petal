package com.petal.browser.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.live.PetalLiveActionBus
import java.util.Locale

/**
 * PetalMediaSessionService
 * Foreground MediaSessionService with notification controls (play, pause, skip, scrub)
 * that binds to WebView video elements or HTML5 audio tags to maintain uninterrupted audio
 * when the screen turns off or the app is minimized.
 */
class PetalMediaSessionService : Service() {

    companion object {
        private const val TAG = "PetalMediaSession"
        const val CHANNEL_ID = "petal_media_playback"
        const val NOTIFICATION_ID = 1002

        const val ACTION_PLAY = "com.petal.browser.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.petal.browser.media.ACTION_PAUSE"
        const val ACTION_STOP = "com.petal.browser.media.ACTION_STOP"
        const val ACTION_SKIP_FORWARD = "com.petal.browser.media.ACTION_SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.petal.browser.media.ACTION_SKIP_BACKWARD"
        const val ACTION_SPEED_TOGGLE = "com.petal.browser.media.ACTION_SPEED_TOGGLE"
        const val ACTION_MUTE_TOGGLE = "com.petal.browser.media.ACTION_MUTE_TOGGLE"
        const val EXTRA_TARGET_ACTIVITY = "extra_target_activity"

        @JvmStatic
        var activeInstance: PetalMediaSessionService? = null
            private set

        private var globalControlListener: MediaControlListener? = null

        @JvmStatic
        fun setGlobalControlListener(listener: MediaControlListener?) {
            globalControlListener = listener
            activeInstance?.setMediaControlListener(listener)
        }

        @JvmStatic
        fun startMediaSession(
            context: Context?,
            title: String?,
            artist: String?,
            playing: Boolean,
            positionMs: Long,
            durationMs: Long,
            artwork: Bitmap?,
            targetActivity: Class<*>?,
            listener: MediaControlListener?
        ) {
            if (context != null) {
                val sp = PreferenceManager.getDefaultSharedPreferences(context)
                if (!sp.getBoolean("sp_mozilla_media_session", true)) {
                    return
                }
            }
            setGlobalControlListener(listener)
            val current = activeInstance
            if (current != null) {
                current.updateMediaState(title, artist, playing, positionMs, durationMs, artwork, targetActivity)
            } else if (context != null) {
                val intent = Intent(context, PetalMediaSessionService::class.java)
                if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        @JvmStatic
        fun stopMediaSession(context: Context?) {
            PetalLiveActionBus.dismissMedia()
            activeInstance?.let { service ->
                @Suppress("DEPRECATION")
                service.stopForeground(true)
                service.stopSelf()
            }
        }
    }

    private val binder: IBinder = LocalBinder()
    private var mediaSession: MediaSessionCompat? = null
    private var mediaCallback: MediaSessionCallback? = null

    private var isPlaying = false
    private var currentTitle = "Web Media"
    private var currentArtist = "Petal Browser"
    private var currentPosition: Long = 0
    private var currentDuration: Long = 0
    private var currentSpeed = 1.0f
    private var isMuted = false
    private var currentArtwork: Bitmap? = null
    private var targetActivityClass: Class<*> = MainActivity::class.java

    private var isNoisyReceiverRegistered = false
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                Log.i(TAG, "Audio becoming noisy (headset disconnected): pausing playback")
                controlListener?.onPause()
                updatePlaybackState(false, currentPosition)
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            registerReceiver(noisyAudioReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            isNoisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyAudioReceiver)
            } catch (_: Exception) {}
            isNoisyReceiverRegistered = false
        }
    }

    interface MediaControlListener {
        fun onPlay()
        fun onPause()
        fun onStop()
        fun onSeekTo(positionMs: Long)
        fun onSpeedToggle(newSpeed: Float) {}
        fun onMuteToggle() {}
        fun onSkip(deltaSeconds: Int) {}
    }

    private var controlListener: MediaControlListener? = null

    inner class LocalBinder : Binder() {
        val service: PetalMediaSessionService
            get() = this@PetalMediaSessionService
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        if (globalControlListener != null) {
            this.controlListener = globalControlListener
        }
        createNotificationChannel()

        val session = MediaSessionCompat(this, TAG).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, this@PetalMediaSessionService, MediaButtonReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(this@PetalMediaSessionService, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE)
            setMediaButtonReceiver(pendingIntent)
            val callback = MediaSessionCallback()
            mediaCallback = callback
            setCallback(callback)
            isActive = true
        }
        mediaSession = session
    }

    override fun onDestroy() {
        PetalLiveActionBus.dismissMedia()
        if (activeInstance === this) {
            activeInstance = null
        }
        unregisterNoisyReceiver()
        mediaSession?.let { session ->
            session.isActive = false
            session.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mediaSession?.let { MediaButtonReceiver.handleIntent(it, intent) }
        val action = intent?.action
        if (action != null) {
            when (action) {
                ACTION_PLAY -> {
                    controlListener?.onPlay()
                    updatePlaybackState(true, currentPosition)
                }
                ACTION_PAUSE -> {
                    controlListener?.onPause()
                    updatePlaybackState(false, currentPosition)
                }
                ACTION_STOP -> {
                    controlListener?.onStop()
                    PetalLiveActionBus.dismissMedia()
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    stopSelf()
                }
                ACTION_SKIP_BACKWARD -> {
                    controlListener?.onSkip(-10)
                }
                ACTION_SKIP_FORWARD -> {
                    controlListener?.onSkip(10)
                }
                ACTION_SPEED_TOGGLE -> {
                    cycleSpeed()
                    controlListener?.onSpeedToggle(currentSpeed)
                    updateMediaState(currentTitle, currentArtist, isPlaying, currentPosition, currentDuration)
                }
                ACTION_MUTE_TOGGLE -> {
                    isMuted = !isMuted
                    controlListener?.onMuteToggle()
                    updateMediaState(currentTitle, currentArtist, isPlaying, currentPosition, currentDuration)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun cycleSpeed() {
        currentSpeed = when (currentSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
    }

    fun setMediaControlListener(listener: MediaControlListener?) {
        this.controlListener = listener
    }

    @JvmOverloads
    fun updateMediaState(
        title: String?,
        artist: String?,
        playing: Boolean,
        positionMs: Long,
        durationMs: Long,
        artwork: Bitmap? = null,
        targetActivity: Class<*>? = MainActivity::class.java
    ) {
        this.currentTitle = if (!title.isNullOrEmpty()) title else "Media"
        this.currentArtist = if (!artist.isNullOrEmpty()) artist else "Petal"
        this.isPlaying = playing
        this.currentPosition = positionMs
        this.currentDuration = durationMs
        if (artwork != null) {
            this.currentArtwork = artwork
        }
        if (targetActivity != null) {
            this.targetActivityClass = targetActivity
        }

        updateMetadata()
        updatePlaybackState(playing, positionMs)

        // Update unified PetalLiveActionBus for Android 16 and Petal NowBar
        PetalLiveActionBus.updateMedia(
            this.currentTitle,
            this.currentArtist,
            playing,
            positionMs,
            durationMs,
            this.currentArtwork
        )

        val notification = buildNotification()
        if (playing) {
            registerNoisyReceiver()
            startForeground(NOTIFICATION_ID, notification)
        } else {
            unregisterNoisyReceiver()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateMetadata() {
        val session = mediaSession ?: return
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration)

        if (currentArtwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArtwork)
        } else {
            try {
                val icon = BitmapFactory.decodeResource(resources, R.drawable.icon_media_play)
                if (icon != null) {
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon)
                }
            } catch (_: Exception) {}
        }

        session.setMetadata(builder.build())
    }

    private fun updatePlaybackState(playing: Boolean, positionMs: Long) {
        val session = mediaSession ?: return
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = (PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs, 1.0f)

        session.setPlaybackState(stateBuilder.build())
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, targetActivityClass).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playIntent = PendingIntent.getService(
            this, 1, Intent(this, PetalMediaSessionService::class.java).setAction(ACTION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this, 2, Intent(this, PetalMediaSessionService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 3, Intent(this, PetalMediaSessionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionToken = mediaSession?.sessionToken
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView(0, 1)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_media_play)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(style)
            .setOngoing(isPlaying)

        currentArtwork?.let { builder.setLargeIcon(it) }

        val speedIntent = PendingIntent.getService(
            this, 4, Intent(this, PetalMediaSessionService::class.java).setAction(ACTION_SPEED_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val muteIntent = PendingIntent.getService(
            this, 5, Intent(this, PetalMediaSessionService::class.java).setAction(ACTION_MUTE_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipBackIntent = PendingIntent.getService(
            this, 6, Intent(this, PetalMediaSessionService::class.java).setAction(ACTION_SKIP_BACKWARD), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipForwardIntent = PendingIntent.getService(
            this, 7, Intent(this, PetalMediaSessionService::class.java).setAction(ACTION_SKIP_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(R.drawable.icon_pip_replay, "-10s", skipBackIntent)
        if (isPlaying) {
            builder.addAction(R.drawable.icon_pause, "Pause", pauseIntent)
        } else {
            builder.addAction(R.drawable.icon_play, "Play", playIntent)
        }
        builder.addAction(R.drawable.icon_pip_forward, "+10s", skipForwardIntent)
        builder.addAction(R.drawable.icon_close, String.format(Locale.US, "%.2fx", currentSpeed), speedIntent)
        builder.addAction(R.drawable.icon_close, if (isMuted) "Unmute" else "Mute", muteIntent)

        // Status bar chip styling & Live Update extras for Android 16+ Promoted Ongoing
        val chipText = if (isPlaying) "Playing" else "Paused"
        val extras = Bundle().apply {
            putString("android.liveAlertText", chipText)
            putBoolean("android.isLiveAlert", true)
            putBoolean("android.promotedOngoing", isPlaying)
            putString("android.shortCriticalText", chipText)
        }
        builder.setExtras(extras)

        try {
            val setShortCriticalText = builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
            setShortCriticalText.invoke(builder, chipText)
        } catch (_: Exception) {}

        // Enable live progress updates & live activity / notification bar compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        val notification = builder.build()
        if (isPlaying && LiveUpdateNotificationManager.canPostPromotedNotifications(this)) {
            try {
                notification.flags = notification.flags or 0x00000200 // FLAG_PROMOTED_ONGOING in Android 16+
            } catch (_: Throwable) {}
        }
        return notification
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Background Media Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Media controls for web audio and background video playback"
                    setSound(null, null)
                    enableVibration(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            controlListener?.onPlay()
            updatePlaybackState(true, currentPosition)
        }

        override fun onPause() {
            controlListener?.onPause()
            updatePlaybackState(false, currentPosition)
        }

        override fun onStop() {
            controlListener?.onStop()
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            controlListener?.onSeekTo(pos)
            currentPosition = pos
            updatePlaybackState(isPlaying, pos)
        }
    }
}
