package com.petal.browser.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity

/**
 * PetalMediaSessionService
 * Foreground MediaSessionService with notification controls (play, pause, skip, scrub)
 * that binds to WebView video elements or HTML5 audio tags to maintain uninterrupted audio
 * when the screen turns off or the app is minimized.
 */
class PetalMediaSessionService : Service() {

    private val binder = LocalBinder()
    private var mediaSession: MediaSessionCompat? = null
    private var mediaCallback: MediaSessionCallback? = null

    private var isPlaying = false
    private var currentTitle = "Web Media"
    private var currentArtist = "Petal Browser"
    private var currentPosition: Long = 0
    private var currentDuration: Long = 0
    private var currentSpeed = 1.0f
    private var isMuted = false

    interface MediaControlListener {
        fun onPlay()
        fun onPause()
        fun onStop()
        fun onSeekTo(positionMs: Long)
        fun onSkipForward(seconds: Int)
        fun onSkipBackward(seconds: Int)
        fun onSpeedToggle(newSpeed: Float)
        fun onMuteToggle(muted: Boolean)
    }

    private var controlListener: MediaControlListener? = null

    fun setMediaControlListener(listener: MediaControlListener?) {
        this.controlListener = listener
    }

    inner class LocalBinder : Binder() {
        val service: PetalMediaSessionService
            get() = this@PetalMediaSessionService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback controls and dynamic scrubbing for browser media"
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaCallback = MediaSessionCallback()
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(mediaCallback)
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    fun updatePlaybackState(
        playing: Boolean,
        title: String?,
        artist: String?,
        positionMs: Long,
        durationMs: Long
    ) {
        this.isPlaying = playing
        if (!title.isNullOrBlank()) this.currentTitle = title
        if (!artist.isNullOrBlank()) this.currentArtist = artist
        this.currentPosition = positionMs
        this.currentDuration = durationMs

        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackSpeed = if (playing) currentSpeed else 0f

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND
            )
            .setState(state, positionMs, playbackSpeed)

        mediaSession?.setPlaybackState(stateBuilder.build())

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)

        try {
            val defaultArt = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            if (defaultArt != null) {
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, defaultArt)
            }
        } catch (ignored: Exception) {
        }

        mediaSession?.setMetadata(metaBuilder.build())

        // Show/update foreground notification
        val notification = buildMediaNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildMediaNotification(): Notification {
        val openAppIntent = Intent(this, BrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.icon_pause,
                "Pause",
                createActionIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.icon_play,
                "Play",
                createActionIntent(ACTION_PLAY)
            )
        }

        val rewAction = NotificationCompat.Action(
            R.drawable.icon_arrow_left,
            "-10s",
            createActionIntent(ACTION_SKIP_BACKWARD)
        )

        val fwdAction = NotificationCompat.Action(
            R.drawable.icon_arrow_right,
            "+10s",
            createActionIntent(ACTION_SKIP_FORWARD)
        )

        val speedTitle = String.format("%.2fx", currentSpeed)
        val speedAction = NotificationCompat.Action(
            R.drawable.icon_speed,
            speedTitle,
            createActionIntent(ACTION_SPEED_TOGGLE)
        )

        val muteAction = NotificationCompat.Action(
            if (isMuted) R.drawable.icon_volume_off else R.drawable.icon_volume_up,
            if (isMuted) "Unmute" else "Mute",
            createActionIntent(ACTION_MUTE_TOGGLE)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_media_play)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(rewAction)
            .addAction(playPauseAction)
            .addAction(fwdAction)
            .addAction(speedAction)
            .addAction(muteAction)

        val sessionToken = mediaSession?.sessionToken
        if (sessionToken != null) {
            val style = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
            builder.setStyle(style)
        }

        return builder.build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, PetalMediaSessionService::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        val action = intent?.action
        if (action != null) {
            when (action) {
                ACTION_PLAY -> controlListener?.onPlay()
                ACTION_PAUSE -> controlListener?.onPause()
                ACTION_STOP -> {
                    controlListener?.onStop()
                    stopForeground(true)
                    stopSelf()
                }
                ACTION_SKIP_FORWARD -> controlListener?.onSkipForward(10)
                ACTION_SKIP_BACKWARD -> controlListener?.onSkipBackward(10)
                ACTION_SPEED_TOGGLE -> {
                    currentSpeed = when (currentSpeed) {
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2.0f
                        else -> 1.0f
                    }
                    controlListener?.onSpeedToggle(currentSpeed)
                    updatePlaybackState(isPlaying, currentTitle, currentArtist, currentPosition, currentDuration)
                }
                ACTION_MUTE_TOGGLE -> {
                    isMuted = !isMuted
                    controlListener?.onMuteToggle(isMuted)
                    updatePlaybackState(isPlaying, currentTitle, currentArtist, currentPosition, currentDuration)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            controlListener?.onPlay()
        }

        override fun onPause() {
            controlListener?.onPause()
        }

        override fun onStop() {
            controlListener?.onStop()
            stopForeground(true)
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            controlListener?.onSeekTo(pos)
        }

        override fun onFastForward() {
            controlListener?.onSkipForward(10)
        }

        override fun onRewind() {
            controlListener?.onSkipBackward(10)
        }
    }

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
    }
}
