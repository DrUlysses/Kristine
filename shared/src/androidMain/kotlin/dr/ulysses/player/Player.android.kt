package dr.ulysses.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dr.ulysses.entities.Song
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import androidx.media3.common.Player as MediaPlayer

@UnstableApi
object PlayerObject {
    var onIsPlayingChangedOnDevice: (Boolean) -> Unit = {}
    var onCurrentPlayingChangedOnDevice: (String?) -> Unit = {}
    private val context: Context by KoinJavaComponent.inject(Context::class.java)

    // The service's own player, set while it runs. The app drives it directly: commands sent through the session
    // would go to a connected server, which the session shows to watches and headsets
    var player: MediaPlayer? = null

    // Connecting starts the service
    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
}

// The player must be called on the main thread
private fun onMain(block: MediaPlayer.() -> Unit) {
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

@UnstableApi
actual fun setUpcomingOnDevice(
    currentIndex: Int,
    upcoming: List<Song>,
) = onMain {
    if (mediaItemCount > currentIndex + 1) {
        removeMediaItems(
            currentIndex + 1,
            mediaItemCount
        )
    }
    addMediaItems(upcoming.map { it.toMediaItem() })
}

@UnstableApi
actual fun setRepeatOnDevice(mode: RepeatMode) = onMain {
    repeatMode = when (mode) {
        RepeatMode.None -> MediaPlayer.REPEAT_MODE_OFF
        RepeatMode.All -> MediaPlayer.REPEAT_MODE_ALL
        RepeatMode.One -> MediaPlayer.REPEAT_MODE_ONE
    }
}
