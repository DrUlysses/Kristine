package dr.ulysses.player

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dr.ulysses.Logger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

@UnstableApi
object PlayerObject {
    var onIsPlayingChangedOnDevice: (Boolean) -> Unit = {}
    var onCurrentPlayingChangedOnDevice: (String?) -> Unit = {}
    private val context: Context by KoinJavaComponent.inject(Context::class.java)
    var player: MediaController? = null

    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync().apply {
        addListener(
            {
                // Call controllerFuture.get() to retrieve the MediaController.
                // MediaController implements the Player interface, so it can be
                // attached to the PlayerView UI component.
                player = get()
            },
            MoreExecutors.directExecutor()
        )
    }
}

@UnstableApi
actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
    PlayerObject.onIsPlayingChangedOnDevice = onChange
}

@UnstableApi
actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
    PlayerObject.onCurrentPlayingChangedOnDevice = onChange
}

@UnstableApi
actual fun setPlayListOnDevice(paths: List<String>) {
    val mediaItems = paths.map { MediaItem.fromUri(it.toUri()) }
    // Ensure MediaController methods are called on the main thread
    MainScope().launch {
        PlayerObject.player?.setMediaItems(mediaItems)
    }
}

@UnstableApi
actual fun pauseCurrentSongOnDevice() {
    // Ensure MediaController methods are called on the main thread
    MainScope().launch {
        PlayerObject.player?.pause()
    }
}

@UnstableApi
actual fun resumeCurrentSongOnDevice() {
    try {
        // Ensure MediaController methods are called on the main thread
        MainScope().launch {
            PlayerObject.player?.apply {
                if (!isPlayingOnDevice()) {
                    prepare()
                    play()
                }
            }
        }
    } catch (e: Exception) {
        // Log the exception but don't crash
        Logger.d(e) { "Error resuming playback." }
    }
}

@UnstableApi
actual fun stopCurrentSongOnDevice() {
    // Ensure MediaController methods are called on the main thread
    MainScope().launch {
        PlayerObject.player?.stop()
    }
}

@UnstableApi
actual fun seekToOnDevice(position: Int) {
    // Ensure MediaController methods are called on the main thread
    MainScope().launch {
        PlayerObject.player?.seekTo((position * 1000L))
    }
}

@UnstableApi
actual fun isPlayingOnDevice(): Boolean = PlayerObject.player?.isPlaying == true

@UnstableApi
actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
    // Ensure MediaController methods are called on the main thread
    MainScope().launch {
        PlayerObject.player?.seekTo(trackNum, 0)
    }
}

@UnstableApi
actual fun playNextOnDevice() {
    try {
        // Ensure MediaController methods are called on the main thread
        MainScope().launch {
            PlayerObject.player?.seekToNextMediaItem()
            resumeCurrentSongOnDevice()
        }
    } catch (e: Exception) {
        // Log the exception but don't crash
        Logger.d(e) { "Error playing next." }
    }
}

@UnstableApi
actual fun playPreviousOnDevice() {
    try {
        // Ensure MediaController methods are called on the main thread
        MainScope().launch {
            PlayerObject.player?.seekToPreviousMediaItem()
            resumeCurrentSongOnDevice()
        }
    } catch (e: Exception) {
        // Log the exception but don't crash
        Logger.d(e) { "Error playing previous." }
    }
}
