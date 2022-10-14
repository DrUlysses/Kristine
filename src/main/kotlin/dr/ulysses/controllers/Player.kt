package dr.ulysses.controllers

import com.soywiz.klock.TimeSpan
import com.soywiz.korau.sound.NativeSoundProvider
import com.soywiz.korau.sound.PlaybackParameters
import com.soywiz.korio.async.launch
import dr.ulysses.entities.Song
import com.soywiz.korio.file.std.resourcesVfs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

object Player {
    private val playlist: MutableList<Song> = mutableListOf()
    private val currentSong: Song? = null
    private var isPlaying: Boolean = false
    private var currentSongPosition: TimeSpan = TimeSpan.ZERO
    private var currentVolume: Double = 0.5
    private val audioPlayer: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val soundProvider: NativeSoundProvider = NativeSoundProvider()

    public fun getNextSong(): Song? =
        playlist.indexOf(currentSong).let {
            playlist.getOrNull(it + 1)
        }

    public fun getPreviousSong(): Song? =
        playlist.indexOf(currentSong).let {
            playlist.getOrNull(it - 1)
        }

    public suspend fun play(song: Song) {
        isPlaying = true
        audioPlayer.launch {
            soundProvider.createSound(resourcesVfs[song.path]).playAndWait(
                params = PlaybackParameters(
                    volume = currentVolume,
                    startTime = currentSongPosition
                )
            )
            isPlaying = false
        }
    }

    public fun pause() {
        isPlaying = false
        audioPlayer.cancel()
    }
}