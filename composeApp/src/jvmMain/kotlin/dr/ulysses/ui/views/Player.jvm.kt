package dr.ulysses.ui.views

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dr.ulysses.player.Player
import dr.ulysses.player.PlayerObject
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import org.jetbrains.skia.Image as SkiaImage

@Composable
actual fun VideoPlayer(
    modifier: Modifier,
) {
    val currentSong = Player.state.currentSong
    val artworkBitmap = remember(currentSong?.path) {
        currentSong?.artwork?.let { artworkData ->
            runCatching {
                val skiaImage = SkiaImage.makeFromEncoded(artworkData)
                skiaImage.asImageBitmap()
            }.getOrNull()
        }
    }

    if (artworkBitmap != null) {
        // Display artwork if available and successfully converted
        Image(
            bitmap = artworkBitmap,
            contentDescription = "Album Artwork",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // If no artwork or conversion failed, use the media player
        DefaultVideoPlayer(modifier, currentSong)
    }
}

@Composable
private fun DefaultVideoPlayer(modifier: Modifier, currentSong: dr.ulysses.entities.Song?) {
    // These remember calls are necessary because they maintain references to native resources
    // that should not be recreated on each recomposition
    val mediaPlayerComponent = remember { initializeMediaPlayerComponent() }
    val mediaPlayer = remember { mediaPlayerComponent.mediaPlayer() }
    val factory = remember { { mediaPlayerComponent } }

    // Play the current song when it changes
    LaunchedEffect(currentSong) {
        if (currentSong != null) {
            mediaPlayer.media().play(currentSong.path)
        }
    }

    // Clean up resources when the composable is disposed
    DisposableEffect(Unit) { onDispose(mediaPlayer::release) }

    SwingPanel(
        factory = factory,
        background = Color.Transparent,
        modifier = modifier
    )
}

private fun initializeMediaPlayerComponent(): EmbeddedMediaPlayerComponent {
    NativeDiscovery().discover()
    val component = EmbeddedMediaPlayerComponent()
    PlayerObject.player.mediaPlayer().setMediaPlayer(
        component.mediaPlayer()
            .apply { events().addMediaEventListener(PlayerObject.stateListener) }
            .apply { events().addMediaPlayerEventListener(PlayerObject.stateListener) })
    PlayerObject.player.events().addMediaListPlayerEventListener(PlayerObject.listListener)
    return component
}

// Platform-specific implementations for player control
actual fun getCurrentPosition(): Int {
    val mediaPlayer = PlayerObject.player.mediaPlayer().mediaPlayer()
    return (mediaPlayer.status().time() / 1000).toInt()
}


actual fun getVolume(): Float {
    val mediaPlayer = PlayerObject.player.mediaPlayer().mediaPlayer()
    return mediaPlayer.audio().volume() / 100f
}

actual fun setVolume(volume: Float) {
    val mediaPlayer = PlayerObject.player.mediaPlayer().mediaPlayer()
    mediaPlayer.audio().setVolume((volume * 100).toInt())
}

actual fun isDesktopOrWasm(): Boolean = true
