package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.player.Player
import dr.ulysses.player.RepeatMode
import dr.ulysses.ui.components.PlayerButtons
import kotlinx.coroutines.delay

@Composable
fun Player(
    currentSong: Song? = Player.state.currentSong,
    isPlaying: Boolean = Player.state.isPlaying,
    isShuffling: Boolean = Player.state.shuffle,
    repeatMode: RepeatMode = Player.state.repeatMode,
    onPreviousCommand: () -> Unit = Player::onPreviousCommand,
    onNextCommand: () -> Unit = Player::onNextCommand,
    onPlayOrPauseCommand: () -> Unit = Player::onPlayOrPauseCommand,
    onToggleShuffleCommand: () -> Unit = Player::onToggleShuffleCommand,
    onSwitchRepeatCommand: () -> Unit = Player::onSwitchRepeatCommand,
    onSeekCommand: (Int) -> Unit = Player::onSeekCommand,
) {
    var currentPosition: Int by remember { mutableStateOf(0) }
    var volume: Float by remember { mutableStateOf(100f) }
    val duration = currentSong?.duration ?: 0
    val showVolumeControl = isDesktopOrWasm()

    LaunchedEffect(Unit) {
        volume = getVolume()
    }

    LaunchedEffect(currentSong, isPlaying) {
        while (isPlaying && currentSong != null) {
            currentPosition = getCurrentPosition()
            delay(500)
        }
    }

    BottomAppBar {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(50.dp)
            ) {
                VideoPlayer(modifier = Modifier.fillMaxSize())
                if (currentSong != null && currentSong.artwork == null) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Song",
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column {
                Row(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        currentSong?.title ?: "No song selected",
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clipToBounds()
                            .weight(5f)
                            .fillMaxWidth(),
                    )

                    // Android has hardware volume keys
                    if (showVolumeControl) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume Control",
                            modifier = Modifier
                                .size(25.dp)
                                .align(Alignment.CenterVertically)
                                .padding(4.dp)
                        )
                        Slider(
                            value = volume,
                            onValueChange = {
                                volume = it
                                setVolume(it)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .align(Alignment.CenterVertically)
                                .padding(4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    )
                    Slider(
                        valueRange = 0f..duration.toFloat(),
                        value = if (duration > 0) {
                            currentPosition.toFloat()
                        } else {
                            0f
                        },
                        onValueChange = {
                            currentPosition = it.toInt()
                            onSeekCommand(currentPosition)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                    )
                    Text(
                        text = formatTime(duration),
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f)
                        .padding(4.dp)
                ) {
                    Row {
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
    }
}

// m:ss
private fun formatTime(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return "0:00"
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
expect fun VideoPlayer(modifier: Modifier)

/** Playback position in seconds. */
expect fun getCurrentPosition(): Int
expect fun getVolume(): Float
expect fun setVolume(volume: Float)
expect fun isDesktopOrWasm(): Boolean
