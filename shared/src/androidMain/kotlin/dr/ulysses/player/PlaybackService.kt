package dr.ulysses.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.*
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dr.ulysses.player.PlayerObject.onCurrentPlayingChangedOnDevice
import dr.ulysses.player.PlayerObject.onIsPlayingChangedOnDevice
import kristine.shared.generated.resources.Res
import kristine.shared.generated.resources.play

class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private val callback by lazy { MediaLibrarySessionCallback(this) }

    companion object {
        private const val NOTIFICATION_ID = 228
        private const val CHANNEL_ID = "kristine_session_notification_channel_id"
    }

    @UnstableApi // MediaSessionService.setListener
    override fun onCreate() {
        super.onCreate()
        val exo: ExoPlayer = ExoPlayer.Builder(this).build().apply {
            val attributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            skipSilenceEnabled = false
            setHandleAudioBecomingNoisy(false)
            setAudioAttributes(attributes, true)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    super.onPlayerError(error)
                    if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                        playNextOnDevice()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    onIsPlayingChangedOnDevice(isPlaying)
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    super.onMediaItemTransition(mediaItem, reason)
                    // The media id is the song path exactly as the app gave it
                    onCurrentPlayingChangedOnDevice(mediaItem?.mediaId)
                }
            })
        }

        // The app shuffles the queue itself; ExoPlayer only repeats as told
        exo.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        mediaSession = MediaLibrarySession.Builder(this, exo, callback).build()

        setListener(MediaSessionServiceListener())
    }

    @OptIn(UnstableApi::class)
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession? = mediaSession

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.let {
            val player = it.player
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    @UnstableApi
    override fun onDestroy() {
        mediaSession?.run {
            release()
            player.release()
        }
        callback.release()
        clearListener()
        super.onDestroy()
    }

    @UnstableApi // MediaSessionService.Listener
    private inner class MediaSessionServiceListener : Listener {

        // Android 12+: a controller tried to resume playback while the service is in the background
        override fun onForegroundServiceStartNotAllowedException() {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // Notification permission is required but not granted
                return
            }
            val notificationManagerCompat = NotificationManagerCompat.from(this@PlaybackService)
            ensureNotificationChannel(notificationManagerCompat)
            val builder =
                NotificationCompat.Builder(this@PlaybackService, CHANNEL_ID)
                    .setSmallIcon(androidx.media3.ui.R.drawable.exo_notification_small_icon)
                    .setContentTitle(Res.string.play.toString())
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(Res.string.play.toString())
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
            notificationManagerCompat.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun ensureNotificationChannel(notificationManagerCompat: NotificationManagerCompat) {
        if (
            Build.VERSION.SDK_INT < 26 ||
            notificationManagerCompat.getNotificationChannel(CHANNEL_ID) != null
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(androidx.media3.session.R.string.default_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        notificationManagerCompat.createNotificationChannel(channel)
    }
}
