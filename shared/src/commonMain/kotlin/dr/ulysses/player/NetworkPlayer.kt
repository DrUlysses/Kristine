package dr.ulysses.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.network.NetworkManager
import dr.ulysses.player.RepeatMode.*

/**
 * Implementation of PlayerService for network playback.
 * This implementation delegates all playback operations to a remote server.
 */
internal class NetworkPlayer : PlayerService {
    override var state: PlayerState by mutableStateOf(PlayerState(isRemotePlaying = true))

    override fun onPlaySongCommand(song: Song) {
        onUpdateSongCommand(song)

        // Send the playlist to the server. It will play the song as well
        val currentSongIndex = state.currentPlaylist.songs.indexOf(song)
        if (currentSongIndex != -1) {
            NetworkManager.sendPlaylistToServer(state.currentPlaylist.songs, currentSongIndex)
        }
    }

    override fun onUpdateSongCommand(song: Song) {
        setState {
            copy(
                currentSong = song,
                currentTrackNum = state.currentPlaylist.songs.indexOf(song)
            )
        }
    }

    override fun onFindSongCommand(query: String): List<Song> = state.currentPlaylist.songs.filter {
        it.title.contains(
            query, ignoreCase = true
        ) || it.artist.contains(query, ignoreCase = true) || it.album?.contains(
            query, ignoreCase = true
        ) == true || it.path.contains(query, ignoreCase = true)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        setState { copy(isPlaying = isPlaying) }
        if (isPlaying) {
            NetworkManager.resumePlaybackOnServer()
        } else {
            NetworkManager.pausePlaybackOnServer()
        }
    }

    override fun onPlayCommand() {
        state.currentSong?.let {
            NetworkManager.playSongOnServer(it)
        }
    }

    override fun onPauseCommand() {
        NetworkManager.pausePlaybackOnServer()
    }

    override fun onResumeCommand() {
        NetworkManager.resumePlaybackOnServer()
    }

    override fun onPlayOrPauseCommand() {
        if (state.isPlaying) {
            onPauseCommand()
        } else {
            onResumeCommand()
        }
    }

    override fun onUpdatePlaybackStateCommand(isPlaying: Boolean) {
        setState { copy(isPlaying = isPlaying) }
    }

    override fun onToggleShuffleCommand() {
        setState { copy(shuffle = !state.shuffle) }
    }

    override fun setShuffle(shuffle: Boolean) {
        setState { copy(shuffle = shuffle) }
    }

    override fun onSwitchRepeatCommand() {
        setState {
            copy(
                repeatMode = when (state.repeatMode) {
                    None -> All
                    All -> One
                    One -> None
                }
            )
        }
    }

    override fun onStopCommand() {
        setState { copy(currentSong = null) }
    }

    override fun onSeekCommand(position: Int) {
        // Network player doesn't support seeking
    }

    override fun onNextCommand() {
        NetworkManager.playNextSongOnServer()
    }

    override fun onPreviousCommand() {
        NetworkManager.playPreviousSongOnServer()
    }

    override fun onPlaylistChanged(playlist: Playlist) {
        setState {
            copy(
                currentPlaylist = playlist,
                currentTrackNum = 0,
            )
        }
    }

    override fun onSongsChanged(songs: List<Song>) {
        setState {
            copy(
                currentPlaylist = state.currentPlaylist.copy(songs = songs),
            )
        }
    }

    private inline fun setState(update: PlayerState.() -> PlayerState) {
        state = state.update()
    }
}
