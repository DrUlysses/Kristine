package dr.ulysses.models

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.Media
import uk.co.caprica.vlcj.media.MediaEventAdapter
import uk.co.caprica.vlcj.media.Meta
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.component.AudioListPlayerComponent
import uk.co.caprica.vlcj.player.list.ListApi
import uk.co.caprica.vlcj.player.list.MediaPlayerApi

object PlayerObject {
    val playerComponent: AudioListPlayerComponent
    val playerList: ListApi
    val player: MediaPlayerApi

    lateinit var onMediaStateChanged: (Boolean) -> Unit
    lateinit var onMediaChanged: (String?) -> Unit

    init {
        NativeDiscovery().discover()
        playerComponent = AudioListPlayerComponent()
        playerList = playerComponent.mediaListPlayer().list()
        player = playerComponent.mediaListPlayer().mediaPlayer()
    }

    val stateListener = object : MediaEventAdapter() {
        override fun mediaStateChanged(media: Media?, newState: State?) {
            onMediaStateChanged(newState == State.PLAYING)
            onMediaChanged(media?.info()?.mrl())
        }

        override fun mediaMetaChanged(media: Media, metaType: Meta) {
            onMediaChanged(media.info()?.mrl())
        }
    }
}

actual fun setPlayListOnDevice(paths: List<String>) {
    PlayerObject.playerList.media().clear()
    paths.map {
        PlayerObject.playerList.media().add(it)
    }
}

actual fun pauseCurrentSongOnDevice() {
    PlayerObject.player.mediaPlayer().controls().pause()
}

actual fun resumeCurrentSongOnDevice() {
    PlayerObject.player.mediaPlayer().controls().play()
}

actual fun stopCurrentSongOnDevice() {
    PlayerObject.player.mediaPlayer().controls().stop()
}

actual fun seekToOnDevice(position: Int) {
    PlayerObject.player.mediaPlayer().controls().setPosition(position.toFloat() / 1000f)
}

actual fun isPlayingOnDevice(): Boolean {
    return PlayerObject.player.mediaPlayer().status().isPlaying
}

actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
    PlayerObject.onMediaStateChanged = onChange
}

actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
    PlayerObject.onMediaChanged = onChange
}

actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
    PlayerObject.playerComponent.mediaListPlayer().controls().play(trackNum)
}

actual fun playNextOnDevice() {
    PlayerObject.playerComponent.mediaListPlayer().controls().playNext()
}

actual fun playPreviousOnDevice() {
    PlayerObject.playerComponent.mediaListPlayer().controls().playPrevious()
}
