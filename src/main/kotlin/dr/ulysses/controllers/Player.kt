package dr.ulysses.controllers

import dr.ulysses.entities.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object Player {
    private val playlist: MutableList<Song> = mutableListOf()
    private val currentSong: Song? = null
    private var isPlaying: Boolean = false
    private var currentVolume: Double = 0.5
    private val audioPlayer: CoroutineScope = CoroutineScope(Dispatchers.IO)

    public fun getNextSong(): Song? =
        playlist.indexOf(currentSong).let {
            playlist.getOrNull(it + 1)
        }

    public fun getPreviousSong(): Song? =
        playlist.indexOf(currentSong).let {
            playlist.getOrNull(it - 1)
        }

    public fun getCurrentSong(): Song? = currentSong

    public suspend fun play(song: Song) {
        isPlaying = true
        audioPlayer.launch {
            isPlaying = false
        }
    }

    public fun pause() {
        isPlaying = false
        audioPlayer.cancel()
    }
}