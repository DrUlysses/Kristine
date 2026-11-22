package dr.ulysses.player

import dr.ulysses.entities.Song
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioListPlayerComponent
import uk.co.caprica.vlcj.player.list.ListApi
import uk.co.caprica.vlcj.player.list.MediaListPlayer
import uk.co.caprica.vlcj.player.list.MediaListPlayerEventAdapter
import uk.co.caprica.vlcj.player.list.PlaybackMode

object PlayerObject {
    lateinit var onMediaStateChanged: (Boolean) -> Unit
    lateinit var onMediaChanged: (String?) -> Unit

    private val playerComponent = run {
        NativeDiscovery().discover()
        AudioListPlayerComponent()
    }
    val player: MediaListPlayer = playerComponent.mediaListPlayer()
    val playerList: ListApi = player.list()

    // The native player behind the list player; player.mediaPlayer().mediaPlayer() stays null in this setup
    val mediaPlayer: MediaPlayer = playerComponent.mediaPlayer()

    // The queue as the app gave it; VLC reports URL-encoded MRLs, which differ from these paths
    var paths: List<String> = emptyList()

    private val playerListener = object : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) {
            onVlcThread { onMediaStateChanged(true) }
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            onVlcThread { onMediaStateChanged(false) }
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            onVlcThread { onMediaStateChanged(false) }
        }

        // An unplayable file is skipped
        override fun error(mediaPlayer: MediaPlayer) {
            onVlcThread {
                if (!mediaPlayer.status().isPlayable) {
                    playNextOnDevice()
                }
            }
        }
    }

    private val listListener = object : MediaListPlayerEventAdapter() {
        // The only event that says which track VLC really plays; it is mapped back by position in the queue
        override fun nextItem(
            mediaListPlayer: MediaListPlayer,
            item: MediaRef,
        ) {
            val media = item.duplicateMedia()
            onVlcThread {
                val mrl = media.info().mrl()
                media.release()
                val index = (0 until playerList.media().count()).firstOrNull { playerList.media().mrl(it) == mrl }
                onMediaChanged(index?.let(paths::getOrNull))
            }
        }

        override fun stopped(mediaListPlayer: MediaListPlayer) {
            onVlcThread { onMediaStateChanged(false) }
        }
    }

    init {
        mediaPlayer.events().addMediaPlayerEventListener(playerListener)
        player.events().addMediaListPlayerEventListener(listListener)
        // Like the app's default, Repeat all
        player.controls().setMode(PlaybackMode.LOOP)
    }

    // vlcj forbids calling libvlc on its event thread (it deadlocks, freezing the UI on the next call),
    // so every handler hands its work to submit()
    private fun onVlcThread(action: () -> Unit) = player.submit(action)
}

actual fun setPlayListOnDevice(songs: List<Song>) {
    val paths = songs.map { it.path }
    PlayerObject.paths = paths
    PlayerObject.playerList.media().clear()
    paths.forEach { PlayerObject.playerList.media().add(it) }
}

actual fun removeFromPlayListOnDevice(index: Int) {
    PlayerObject.paths = PlayerObject.paths.filterIndexed { i, _ -> i != index }
    PlayerObject.playerList.media().remove(index)
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
    PlayerObject.mediaPlayer.controls().setTime(position * 1000L)
}

actual fun isPlayingOnDevice(): Boolean = PlayerObject.player.status().isPlaying

actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
    PlayerObject.onMediaStateChanged = onChange
}

actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
    PlayerObject.onMediaChanged = onChange
}

actual fun playTrackOnDevice(trackNum: Int) {
    PlayerObject.player.controls().play(trackNum)
}

// The device skipping on its own (unplayable file); the app reacts to the resulting nextItem event
internal fun playNextOnDevice() {
    PlayerObject.player.controls().playNext()
}

actual fun setUpcomingOnDevice(
    currentIndex: Int,
    upcoming: List<Song>,
) {
    val media = PlayerObject.playerList.media()
    // Only entries after the current one change: VLC keeps its place in the list by index
    while (media.count() > currentIndex + 1) {
        media.remove(media.count() - 1)
    }
    upcoming.forEach { media.add(it.path) }
    PlayerObject.paths = PlayerObject.paths.take(currentIndex + 1) + upcoming.map { it.path }
}

actual fun setRepeatOnDevice(mode: RepeatMode) {
    PlayerObject.player.controls().setMode(
        when (mode) {
            RepeatMode.None -> PlaybackMode.DEFAULT
            RepeatMode.All -> PlaybackMode.LOOP
            RepeatMode.One -> PlaybackMode.REPEAT
        }
    )
}
