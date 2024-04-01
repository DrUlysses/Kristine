package dr.ulysses.models

import dr.ulysses.models.PlayerObject.onMediaChanged
import dr.ulysses.models.PlayerObject.playerComponent
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.Media
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.media.Meta
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.component.AudioListPlayerComponent
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import uk.co.caprica.vlcj.player.list.ListApi
import uk.co.caprica.vlcj.player.list.MediaListPlayer
import uk.co.caprica.vlcj.player.list.MediaListPlayerEventAdapter

object PlayerObject {
    val playerComponent: AudioListPlayerComponent
    val playerList: ListApi
    val player: MediaListPlayer

    lateinit var onMediaStateChanged: (Boolean) -> Unit
    lateinit var onMediaChanged: (String?) -> Unit

    init {
        NativeDiscovery().discover()
        playerComponent = AudioListPlayerComponent()
        player = playerComponent.mediaListPlayer()
        playerList = player.list()
    }

    val stateListener = object : AudioPlayerComponent() {
        override fun mediaStateChanged(media: Media?, newState: State?) {
            super.mediaStateChanged(media, newState)
            if (newState == State.PLAYING)
                onMediaStateChanged(true)
            onMediaChanged(playerComponent.mediaListPlayer().list().media().mrl(PlayerService.state.currentTrackNum))
        }

        override fun mediaMetaChanged(media: Media, metaType: Meta) {
            super.mediaMetaChanged(media, metaType)
            onMediaChanged(playerComponent.mediaListPlayer().list().media().mrl(PlayerService.state.currentTrackNum))
        }

        override fun paused(mediaPlayer: MediaPlayer?) {
            super.paused(mediaPlayer)
            onMediaStateChanged(false)
        }

        override fun stopped(mediaPlayer: MediaPlayer?) {
            super.stopped(mediaPlayer)
            onMediaStateChanged(false)
        }

        override fun error(mediaPlayer: MediaPlayer?) {
            super.error(mediaPlayer)
            if (mediaPlayer?.status()?.isPlayable == false)
                playNextOnDevice()
        }
    }

    val listListener = object : MediaListPlayerEventAdapter() {
        override fun nextItem(mediaListPlayer: MediaListPlayer, item: MediaRef) {
            super.nextItem(mediaListPlayer, item)
            onMediaChanged(item.duplicateMedia().info().mrl())
        }

        override fun stopped(mediaListPlayer: MediaListPlayer) {
            super.stopped(mediaListPlayer)
            onMediaStateChanged(false)
        }
    }
}

actual fun setPlayListOnDevice(paths: List<String>) {
    PlayerObject.playerList.media().clear()
    paths.map { PlayerObject.playerList.media().add(it) }
    onMediaChanged(playerComponent.mediaListPlayer().list().media().mrl(PlayerService.state.currentTrackNum))
}

actual fun pauseCurrentSongOnDevice() {
    PlayerObject.player.controls().pause()
}

actual fun resumeCurrentSongOnDevice() {
    PlayerObject.player.controls().play()
}

actual fun stopCurrentSongOnDevice() {
    PlayerObject.player.controls().stop()
}

actual fun seekToOnDevice(position: Int) {
    PlayerObject.player.mediaPlayer().mediaPlayer().controls().setPosition(position.toFloat() / 1000f)
}

actual fun isPlayingOnDevice(): Boolean {
    return PlayerObject.player.status().isPlaying
}

actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
    PlayerObject.onMediaStateChanged = onChange
}

actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
    onMediaChanged = onChange
}

actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
    PlayerObject.player.controls().play(trackNum)
    onMediaChanged(playerComponent.mediaListPlayer().list().media().mrl(PlayerService.state.currentTrackNum))
}

actual fun playNextOnDevice() {
    PlayerObject.player.controls().playNext()
}

actual fun playPreviousOnDevice() {
    PlayerObject.player.controls().playPrevious()
}
