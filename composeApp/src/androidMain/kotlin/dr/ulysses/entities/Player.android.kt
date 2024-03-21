package dr.ulysses.entities

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.koin.java.KoinJavaComponent
import dr.ulysses.entities.Player as PlayerEntity

object PlayerObject {
    private val context: Context by KoinJavaComponent.inject(Context::class.java)
    val exo = ExoPlayer.Builder(context).build().apply {
        skipSilenceEnabled = true
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (!isPlaying)
                    PlayerEntity.currentSong = null
            }
        })
    }
}

actual fun playSong(song: Song) {
    val mediaItem = MediaItem.fromUri(Uri.parse(song.path))
    PlayerObject.exo.setMediaItem(mediaItem)
    PlayerObject.exo.prepare()
    PlayerObject.exo.play()
}

actual fun pauseCurrentSong() {
    PlayerObject.exo.pause()
}

actual fun resumeCurrentSong() {
    PlayerObject.exo.play()
}

actual fun stopCurrentSong() {
    PlayerObject.exo.stop()
}

actual fun seekTo(position: Int) {
    PlayerObject.exo.seekTo(position.toLong())
}

actual fun isPlaying(): Boolean {
    return PlayerObject.exo.isPlaying
}
