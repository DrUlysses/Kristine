package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.player.RepeatMode
import dr.ulysses.ui.components.PlayerButtons

@Composable
fun Player(
    currentSong: Song?,
    isPlaying: Boolean,
    isShuffling: Boolean,
    repeatMode: RepeatMode,
    onPreviousCommand: () -> Unit,
    onNextCommand: () -> Unit,
    onPlayOrPauseCommand: () -> Unit,
    onToggleShuffleCommand: () -> Unit,
    onSwitchRepeatCommand: () -> Unit,
) {
    BottomAppBar {
        Row {
            VideoPlayer(modifier = Modifier.width(64.dp))
            Column {
                // TODO: Icon and song info
                Text(
                    currentSong?.title ?: "No song selected",
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clipToBounds()
                        .fillMaxWidth(),
                )
                Spacer(modifier = Modifier.width(8.dp))
                PlayerButtons(
                    isPlaying = isPlaying,
                    onPlayOrPauseCommand = onPlayOrPauseCommand,
                    onPreviousCommand = onPreviousCommand,
                    onNextCommand = onNextCommand,
                    onToggleShuffleCommand = onToggleShuffleCommand,
                    onSwitchRepeatCommand = onSwitchRepeatCommand,
                    isShuffling = isShuffling,
                    repeatMode = repeatMode,
                )
            }
        }
    }
}

@Composable
expect fun VideoPlayer(modifier: Modifier)
