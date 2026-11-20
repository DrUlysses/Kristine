package dr.ulysses.ui.elements

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dr.ulysses.entities.Song
import dr.ulysses.player.Player

/**
 * Plays [songs] from the first, or pauses and resumes them while one of them is the player's song.
 */
@Composable
fun PlayButton(
    text: String,
    songs: List<Song>,
) {
    val isPlaying = isPlayingFrom(songs)
    FilledTonalButton(
        onClick = { playOrPause(songs) },
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Icon(
            imageVector = playIcon(isPlaying),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(
            modifier = Modifier.size(ButtonDefaults.IconSpacing)
        )
        Text(
            if (isPlaying) {
                "Pause"
            } else {
                text
            }
        )
    }
}

/**
 * [PlayButton] as an icon, for a row.
 */
@Composable
fun PlayIconButton(
    name: String,
    songs: List<Song>,
) {
    val isPlaying = isPlayingFrom(songs)
    IconButton(
        onClick = { playOrPause(songs) }
    ) {
        Icon(
            imageVector = playIcon(isPlaying),
            contentDescription = if (isPlaying) {
                "Pause"
            } else {
                "Play $name"
            }
        )
    }
}

/**
 * Queues [songs] and plays [first] of them.
 */
fun play(
    songs: List<Song>,
    first: Song,
) {
    Player.onSongsChanged(songs)
    Player.onPlaySongCommand(first)
}

// By path: Song.equals compares tags, which copies share. Read in composition, so the button follows the player
private fun isPlayingFrom(songs: List<Song>) =
    Player.state.isPlaying && songs.any { it.path == Player.state.currentSong?.path }

private fun playOrPause(songs: List<Song>) {
    if (songs.any { it.path == Player.state.currentSong?.path }) {
        Player.onPlayOrPauseCommand()
    } else {
        play(
            songs = songs,
            first = songs.first()
        )
    }
}

private fun playIcon(isPlaying: Boolean) = if (isPlaying) {
    Icons.Filled.Pause
} else {
    Icons.Filled.PlayArrow
}
