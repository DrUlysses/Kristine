package dr.ulysses.models

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.koin.java.KoinJavaComponent

@UnstableApi
object PlayerObject {
    private val context: Context by KoinJavaComponent.inject(Context::class.java)
    val exo = ExoPlayer.Builder(context).build().apply {
        skipSilenceEnabled = true
    }
}

@UnstableApi
actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
    PlayerObject.exo.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            onChange(isPlaying)
        }
    })
}

@UnstableApi
actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
    PlayerObject.exo.addListener(object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            onChange(mediaItem?.localConfiguration?.uri?.toString())
        }
    })
}

@UnstableApi
actual fun setPlayListOnDevice(paths: List<String>) {
    val mediaItems = paths.map { MediaItem.fromUri(Uri.parse(it)) }
    PlayerObject.exo.setMediaItems(mediaItems)
}

@UnstableApi
actual fun pauseCurrentSongOnDevice() {
    PlayerObject.exo.pause()
}

@UnstableApi
actual fun resumeCurrentSongOnDevice() {
    PlayerObject.exo.prepare()
    PlayerObject.exo.play()
}

@UnstableApi
actual fun stopCurrentSongOnDevice() {
    PlayerObject.exo.stop()
}

@UnstableApi
actual fun seekToOnDevice(position: Int) {
    PlayerObject.exo.seekTo(position.toLong())
}

@UnstableApi
actual fun isPlayingOnDevice(): Boolean {
    return PlayerObject.exo.isPlaying
}

@UnstableApi
actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
    PlayerObject.exo.seekTo(trackNum, 0)
}

@UnstableApi
actual fun playNextOnDevice() {
    PlayerObject.exo.seekToNextMediaItem()
}

@UnstableApi
actual fun playPreviousOnDevice() {
    PlayerObject.exo.seekToPreviousMediaItem()
}
