package dr.ulysses.ui.views

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dr.ulysses.entities.SongRepository
import dr.ulysses.player.Player
import dr.ulysses.player.PlayerObject
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

@Composable
actual fun VideoPlayer(
    modifier: Modifier,
) {
    val currentSong = Player.state.currentSong
    val context = LocalPlatformContext.current
    var artworkData by remember(currentSong?.path) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(currentSong?.path) {
        artworkData = currentSong?.let { SongRepository.getArtwork(it.path) }
    }

    if (artworkData != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artworkData)
                .build(),
            contentDescription = "Album Artwork",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        DefaultVideoPlayer(modifier, currentSong)
    }
}

@Composable
private fun DefaultVideoPlayer(modifier: Modifier, currentSong: dr.ulysses.entities.Song?) {
    // Native VLC resources: created once, released on dispose
    val mediaPlayerComponent = remember { initializeMediaPlayerComponent() }
    val mediaPlayer = remember { mediaPlayerComponent.mediaPlayer() }
    val factory = remember { { mediaPlayerComponent.apply { background = java.awt.Color(0, 0, 0, 0) } } }

    LaunchedEffect(currentSong) {
        if (currentSong != null) {
            mediaPlayer.media().play(currentSong.path)
        }
    }

    DisposableEffect(Unit) { onDispose(mediaPlayer::release) }

    SwingPanel(
        factory = factory,
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

// Seconds, like the song duration the slider compares it with
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
