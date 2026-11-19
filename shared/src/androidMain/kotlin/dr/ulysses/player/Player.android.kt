package dr.ulysses.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dr.ulysses.entities.Song
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
        addListener({ player = get() }, MoreExecutors.directExecutor())
    }
}

// MediaController must be called on the main thread
private fun onMain(block: MediaController.() -> Unit) {
    MainScope().launch { PlayerObject.player?.block() }
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
actual fun setPlayListOnDevice(songs: List<Song>) {
    val mediaItems = songs.map { it.toMediaItem() }
    onMain { setMediaItems(mediaItems) }
}

@UnstableApi
actual fun removeFromPlayListOnDevice(index: Int) = onMain { removeMediaItem(index) }

@UnstableApi
actual fun pauseCurrentSongOnDevice() = onMain { pause() }

@UnstableApi
actual fun resumeCurrentSongOnDevice() = onMain {
    if (!isPlaying) {
        prepare()
        play()
    }
}

@UnstableApi
actual fun stopCurrentSongOnDevice() = onMain { stop() }

@UnstableApi
actual fun seekToOnDevice(position: Int) = onMain { seekTo(position * 1000L) }

@UnstableApi
actual fun isPlayingOnDevice(): Boolean = PlayerObject.player?.isPlaying == true

@UnstableApi
actual fun playTrackOnDevice(trackNum: Int) = onMain {
    seekTo(trackNum, 0)
    prepare()
    play()
}

@UnstableApi
// The device skipping on its own (unplayable file); the app reacts to the resulting transition event
internal fun playNextOnDevice() = onMain {
    seekToNextMediaItem()
    resumeCurrentSongOnDevice()
}
