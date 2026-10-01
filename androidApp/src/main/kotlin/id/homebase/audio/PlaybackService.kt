package id.homebase.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as SessionState
import android.os.IBinder
import id.homebase.audio.playback.PlaybackController
import id.homebase.audio.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps playback alive while the app is in the background and mirrors it into a media session,
 * which gives the notification and lock screen their transport controls.
 */
class PlaybackService : Service() {
    private val playback: PlaybackController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSession
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.playback_channel), NotificationManager.IMPORTANCE_LOW)
        )
        session = MediaSession(this, "HomebaseAudio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = playback.togglePlayPause()
                override fun onPause() = playback.pause()
                override fun onSkipToNext() = playback.next()
                override fun onSkipToPrevious() = playback.previous()
                override fun onSeekTo(pos: Long) = playback.seekTo(pos)
                override fun onStop() = playback.stop()
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }
        scope.launch { playback.state.collect(::render) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> playback.togglePlayPause()
            ACTION_NEXT -> playback.next()
            ACTION_PREVIOUS -> playback.previous()
        }
        // startForegroundService requires startForeground within seconds, whatever the state.
        if (!foreground) render(playback.state.value)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    private fun render(state: PlaybackState) {
        val track = state.current
        if (track == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
            return
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                .build()
        )
        session.setPlaybackState(
            SessionState.Builder()
                .setActions(
                    SessionState.ACTION_PLAY or SessionState.ACTION_PAUSE or SessionState.ACTION_PLAY_PAUSE or
                        SessionState.ACTION_SKIP_TO_NEXT or SessionState.ACTION_SKIP_TO_PREVIOUS or
                        SessionState.ACTION_SEEK_TO or SessionState.ACTION_STOP
                )
                .setState(
                    when {
                        state.isLoading -> SessionState.STATE_BUFFERING
                        state.isPlaying -> SessionState.STATE_PLAYING
                        else -> SessionState.STATE_PAUSED
                    },
                    state.positionMs,
                    if (state.isPlaying) 1f else 0f,
                )
                .build()
        )
        val notification = notification(state, track.title)
        if (state.isPlaying || state.isLoading || !foreground) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            foreground = true
        }
        if (!state.isPlaying && !state.isLoading) {
            // Paused: the notification stays, but the system may now stop the service.
            stopForeground(STOP_FOREGROUND_DETACH)
            foreground = false
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(state: PlaybackState, title: String): Notification {
        val playing = state.isPlaying || state.isLoading
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentIntent(openAppIntent())
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_previous, R.string.playback_previous, ACTION_PREVIOUS))
            .addAction(
                if (playing) action(android.R.drawable.ic_media_pause, R.string.playback_pause, ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, R.string.playback_play, ACTION_TOGGLE)
            )
            .addAction(action(android.R.drawable.ic_media_next, R.string.playback_next, ACTION_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun action(icon: Int, label: Int, action: String): Notification.Action {
        val intent = PendingIntent.getService(
            this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), intent).build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "id.homebase.audio.TOGGLE"
        private const val ACTION_NEXT = "id.homebase.audio.NEXT"
        private const val ACTION_PREVIOUS = "id.homebase.audio.PREVIOUS"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PlaybackService::class.java))
        }
    }
}
