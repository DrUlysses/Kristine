package dr.ulysses.models

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dr.ulysses.player.PlaybackService
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
    val mediaItems = paths.map { MediaItem.fromUri(Uri.parse(it)) }
    PlayerObject.player?.setMediaItems(mediaItems)
}

@UnstableApi
actual fun pauseCurrentSongOnDevice() {
    PlayerObject.player?.pause()
}

@UnstableApi
actual fun resumeCurrentSongOnDevice() {
    PlayerObject.player?.prepare()
    PlayerObject.player?.play()
}

@UnstableApi
actual fun stopCurrentSongOnDevice() {
    PlayerObject.player?.stop()
}

@UnstableApi
actual fun seekToOnDevice(position: Int) {
    PlayerObject.player?.seekTo(position.toLong())
}

@UnstableApi
actual fun isPlayingOnDevice(): Boolean {
    return PlayerObject.player?.isPlaying == true
}

@UnstableApi
actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
    PlayerObject.player?.seekTo(trackNum, 0)
}

@UnstableApi
actual fun playNextOnDevice() {
    PlayerObject.player?.seekToNextMediaItem()
    resumeCurrentSongOnDevice()
}

@UnstableApi
actual fun playPreviousOnDevice() {
    PlayerObject.player?.seekToPreviousMediaItem()
    resumeCurrentSongOnDevice()
}
