package dr.ulysses.ui.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import dr.ulysses.models.PlayerObject
import dr.ulysses.models.PlayerService
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent


@Composable
actual fun VideoPlayer(
    modifier: Modifier,
) {
    val mediaPlayerComponent = remember { initializeMediaPlayerComponent() }
    val mediaPlayer = remember { mediaPlayerComponent.mediaPlayer() }

    val factory = remember { { mediaPlayerComponent } }
    /* OR the following code and using SwingPanel(factory = { factory }, ...) */
    // val factory by rememberUpdatedState(mediaPlayerComponent)

    LaunchedEffect(PlayerService.state.currentSong) {
        mediaPlayer.media().play/*OR .start*/(PlayerService.state.currentSong?.path ?: "")
    }
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
