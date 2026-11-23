package dr.ulysses.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dr.ulysses.entities.Song
import androidx.media3.common.Player as MediaPlayer

/**
 * The device's player for the media session, so watches, headsets and the notification work also while the app
 * controls a server: the session then shows the server's song, and its buttons go to the server.
 */
@UnstableApi
internal class ServerMirroringPlayer(player: MediaPlayer) : ForwardingSimpleBasePlayer(player) {
    private val main = Handler(Looper.getMainLooper())

    init {
        // State changes come from the network threads too; the session reads its player on the main thread
        Player.onStateChanged = { main.post { invalidateState() } }
    }

    private val isMirroring
        get() = Player.state.isRemotePlaying

    // The current song and its neighbors: enough for next and previous to be offered
    private fun neighbors(): Pair<List<Song>, Int> {
        val songs = Player.state.currentPlaylist.songs
        val current = Player.state.currentTrackNum.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        val from = (current - 1).coerceAtLeast(0)
        return songs.subList(from, (current + 2).coerceAtMost(songs.size)) to current - from
    }

    override fun getState(): State {
        val state = super.getState()
        if (!isMirroring) {
            return state
        }
        val (songs, current) = neighbors()
        if (Player.state.currentSong == null || songs.isEmpty()) {
            return state.buildUpon()
                .setPlaylist(emptyList())
                .setPlaybackState(STATE_IDLE)
                .setPlayWhenReady(false, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                .build()
        }
        val position = PositionSupplier { Player.position() * 1000L }
        return state.buildUpon()
            .setAvailableCommands(COMMANDS)
            .setPlaylist(songs.mapIndexed { index, song ->
                // A queue may hold a song twice; the ids must differ
                MediaItemData.Builder("$index ${song.path}")
                    .setMediaItem(song.toMediaItem())
                    .setDurationUs(song.duration?.let { it * 1_000_000L } ?: C.TIME_UNSET)
                    .build()
            })
            .setCurrentMediaItemIndex(current)
            .setContentPositionMs(position)
            .setContentBufferedPositionMs(position)
            .setPlaybackState(STATE_READY)
            .setPlayWhenReady(Player.state.isPlaying, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setIsLoading(false)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (!isMirroring) {
            return super.handleSetPlayWhenReady(playWhenReady)
        }
        if (playWhenReady) {
            Player.onResumeCommand()
        } else {
            Player.onPauseCommand()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (!isMirroring) {
            return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        val current = neighbors().second
        when {
            mediaItemIndex > current -> Player.onNextCommand()
            mediaItemIndex in 0..<current -> Player.onPreviousCommand()
            else -> Player.onSeekCommand((positionMs.coerceAtLeast(0) / 1000).toInt())
        }
        return Futures.immediateVoidFuture()
    }

    // The server isn't stopped from here: the notification's dismissal only ends this device's playback
    override fun handlePrepare(): ListenableFuture<*> = if (isMirroring) {
        Futures.immediateVoidFuture()
    } else {
        super.handlePrepare()
    }

    private companion object {
        val COMMANDS = MediaPlayer.Commands.Builder().addAll(
            COMMAND_PLAY_PAUSE,
            COMMAND_PREPARE,
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_MEDIA_ITEM,
            COMMAND_GET_CURRENT_MEDIA_ITEM,
            COMMAND_GET_TIMELINE,
            COMMAND_GET_METADATA,
            COMMAND_GET_AUDIO_ATTRIBUTES,
        ).build()
    }
}
