package dr.ulysses.player

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

    // vlcj forbids calling libvlc on its event thread (it deadlocks, freezing the UI on the next call),
    // so every handler hands its work to submit()
    private fun onVlcThread(action: () -> Unit) = player.submit(action)

    val stateListener = object : AudioPlayerComponent() {
        override fun mediaStateChanged(media: Media?, newState: State?) {
            super.mediaStateChanged(media, newState)
            onVlcThread {
                if (newState == State.PLAYING) onMediaStateChanged(true)
                onMediaChanged(playerList.media().mrl(Player.state.currentTrackNum))
            }
        }

        override fun mediaMetaChanged(media: Media, metaType: Meta) {
            super.mediaMetaChanged(media, metaType)
            onVlcThread { onMediaChanged(playerList.media().mrl(Player.state.currentTrackNum)) }
        }

        override fun paused(mediaPlayer: MediaPlayer?) {
            super.paused(mediaPlayer)
            onVlcThread { onMediaStateChanged(false) }
        }

        override fun stopped(mediaPlayer: MediaPlayer?) {
            super.stopped(mediaPlayer)
            onVlcThread { onMediaStateChanged(false) }
        }

        // An unplayable file, e.g. one deleted since the last scan, is skipped
        override fun error(mediaPlayer: MediaPlayer?) {
            super.error(mediaPlayer)
            onVlcThread { if (mediaPlayer?.status()?.isPlayable == false) playNextOnDevice() }
        }
    }

    val listListener = object : MediaListPlayerEventAdapter() {
        override fun nextItem(mediaListPlayer: MediaListPlayer, item: MediaRef) {
            super.nextItem(mediaListPlayer, item)
            val media = item.duplicateMedia()
            onVlcThread {
                onMediaChanged(media.info().mrl())
                media.release()
            }
        }

        override fun stopped(mediaListPlayer: MediaListPlayer) {
            super.stopped(mediaListPlayer)
            onVlcThread { onMediaStateChanged(false) }
        }
    }
}

actual fun setPlayListOnDevice(paths: List<String>) {
    PlayerObject.playerList.media().clear()
    paths.map { PlayerObject.playerList.media().add(it) }
    PlayerObject.onMediaChanged(
        PlayerObject.playerComponent.mediaListPlayer().list().media().mrl(Player.state.currentTrackNum)
    )
}

actual fun pauseCurrentSongOnDevice() {
    PlayerObject.player.controls().pause()
    PlayerObject.onMediaStateChanged(false)
}

actual fun resumeCurrentSongOnDevice() {
    PlayerObject.player.controls().play()
    PlayerObject.onMediaStateChanged(true)
}

actual fun stopCurrentSongOnDevice() {
    PlayerObject.player.controls().stop()
    PlayerObject.onMediaStateChanged(false)
}

actual fun seekToOnDevice(position: Int) {
    PlayerObject.player.mediaPlayer().mediaPlayer().controls().setTime(position * 1000L)
}

actual fun isPlayingOnDevice(): Boolean {
    return PlayerObject.player.status().isPlaying
}

actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
    PlayerObject.onMediaStateChanged = onChange
}

actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
    PlayerObject.onMediaChanged = onChange
}

actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
    PlayerObject.player.controls().play(trackNum)
    PlayerObject.onMediaChanged(
        PlayerObject.playerComponent.mediaListPlayer().list().media().mrl(Player.state.currentTrackNum)
    )
}

actual fun playNextOnDevice() {
    PlayerObject.player.controls().playNext()
}

actual fun playPreviousOnDevice() {
    PlayerObject.player.controls().playPrevious()
}
