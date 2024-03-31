package dr.ulysses.models

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.*
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.component.AudioListPlayerComponent
import uk.co.caprica.vlcj.player.list.ListApi
import uk.co.caprica.vlcj.player.list.MediaPlayerApi

object PlayerObject {
    val playerComponent: AudioListPlayerComponent
    val playerList: ListApi
    val player: MediaPlayerApi

    init {
        NativeDiscovery().discover()
        playerComponent = AudioListPlayerComponent()
        playerList = playerComponent.mediaListPlayer().list()
        player = playerComponent.mediaListPlayer().mediaPlayer()
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
    PlayerObject.player.mediaPlayer()?.events()?.addMediaEventListener(object : MediaEventListener {
        override fun mediaMetaChanged(media: Media?, metaType: Meta?) {}
        override fun mediaSubItemAdded(media: Media?, newChild: MediaRef?) {}
        override fun mediaDurationChanged(media: Media?, newDuration: Long) {}
        override fun mediaParsedChanged(media: Media?, newStatus: MediaParsedStatus?) {}
        override fun mediaFreed(media: Media?, mediaFreed: MediaRef?) {}
        override fun mediaSubItemTreeAdded(media: Media?, item: MediaRef?) {}
        override fun mediaThumbnailGenerated(media: Media?, picture: Picture?) {}

        override fun mediaStateChanged(media: Media?, newState: State?) {
            onChange(newState == State.PLAYING)
        }
    })
}

actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
    PlayerObject.player.mediaPlayer()?.events()?.addMediaEventListener(object : MediaEventListener {
        override fun mediaMetaChanged(media: Media?, metaType: Meta?) {}
        override fun mediaSubItemAdded(media: Media?, newChild: MediaRef?) {}
        override fun mediaDurationChanged(media: Media?, newDuration: Long) {}
        override fun mediaParsedChanged(media: Media?, newStatus: MediaParsedStatus?) {}
        override fun mediaFreed(media: Media?, mediaFreed: MediaRef?) {}
        override fun mediaSubItemTreeAdded(media: Media?, item: MediaRef?) {}
        override fun mediaThumbnailGenerated(media: Media?, picture: Picture?) {}

        override fun mediaStateChanged(media: Media?, newState: State?) {
            onChange(media?.info()?.mrl())
        }
    })
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
