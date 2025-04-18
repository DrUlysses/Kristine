package dr.ulysses.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dr.ulysses.player.Player
import dr.ulysses.player.PlayerObject.onCurrentPlayingChangedOnDevice
import dr.ulysses.player.PlayerObject.onIsPlayingChangedOnDevice
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.play

class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null

    companion object {
        private const val NOTIFICATION_ID = 228
        private const val CHANNEL_ID = "kristine_session_notification_channel_id"
    }

    /**
     * Returns the single top session activity. It is used by the notification when the app task is
     * active and an activity is in the fore or background.
     *
     * Tapping the notification then typically should trigger a single top activity. This way, the
     * user navigates to the previous activity when pressing back.
     *
     * If null is returned, [MediaSession.setSessionActivity] is not set by the demo service.
     */
    fun getSingleTopActivity(): PendingIntent? = null

    /**
     * Returns a back stacked session activity that is used by the notification when the service is
     * running standalone as a foreground service. This is typically the case after the app has been
     * dismissed from the recent tasks, or after automatic playback resumption.
     *
     * Typically, a playback activity should be started with a stack of activities underneath. This
     * way, when pressing back, the user doesn't land on the home screen of the device, but on an
     * activity defined in the back stack.
     *
     * See [androidx.core.app.TaskStackBuilder] to construct a back stack.
     *
     * If null is returned, [MediaSession.setSessionActivity] is not set by the demo service.
     */
    fun getBackStackedActivity(): PendingIntent? = null

    /**
     * Creates the library session callback to implement the domain logic. Can be overridden to return
     * an alternative callback, for example a subclass of [MediaLibrarySessionCallback].
     *
     * This method is called when the session is built by the [PlaybackService].
     */
    fun createLibrarySessionCallback(): MediaLibrarySession.Callback {
        return MediaLibrarySessionCallback(this)
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
                    if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
                        playNextOnDevice()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    onIsPlayingChangedOnDevice(isPlaying)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    onCurrentPlayingChangedOnDevice(mediaItem?.localConfiguration?.uri?.toString())
                }
            })
        }

        val callback = MediaLibrarySessionCallback(this)

        mediaSession = MediaLibrarySession.Builder(this, exo, callback).build().apply {
            player.shuffleModeEnabled = Player.state.shuffle
        }

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

    // MediaSession.setSessionActivity
    // MediaSessionService.clearListener
    @UnstableApi
    override fun onDestroy() {
        mediaSession?.let { mediaSession ->
            getBackStackedActivity()?.let { mediaSession.setSessionActivity(it) }
            mediaSession.release()
            mediaSession.player.release()
            clearListener()
            super.onDestroy()
        }
    }

    @UnstableApi // MediaSessionService.Listener
    private inner class MediaSessionServiceListener : Listener {

        /**
         * This method is only required to be implemented on Android 12 or above when an attempt is made
         * by a media controller to resume playback when the {@link MediaSessionService} is in the
         * background.
         */
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
                    .also { builder -> getBackStackedActivity()?.let { builder.setContentIntent(it) } }
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
