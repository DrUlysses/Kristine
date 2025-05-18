package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
    // State that needs to be remembered across recompositions
    var currentPosition: Int by remember { mutableStateOf(0) }
    var volume: Float by remember { mutableStateOf(0f) }

    // Derived state that doesn't need remembers
    val duration = currentSong?.duration ?: 0
    val showVolumeControl = isDesktopOrWasm()

    // Initialize volume once
    LaunchedEffect(Unit) {
        volume = getVolume()
    }

    // Update position periodically
    LaunchedEffect(currentSong, isPlaying) {
        while (isPlaying && currentSong != null) {
            currentPosition = getCurrentPosition()
            delay(500) // Update more frequently for smoother slider movement
        }
    }

    BottomAppBar {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Song icon and video player
            Box(modifier = Modifier.size(48.dp)) {
                VideoPlayer(modifier = Modifier.fillMaxSize())
                // Only show music note icon if there's no artwork
                if (currentSong != null && currentSong.artwork == null) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Song",
                        modifier = Modifier.size(24.dp).align(Alignment.Center)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                // Song title
                Text(
                    currentSong?.title ?: "No song selected",
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clipToBounds()
                        .fillMaxWidth(),
                )

                // Progress bar and time display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(formatTime(currentPosition))
                    Slider(
                        valueRange = 0f..duration.toFloat(),
                        value = if (duration > 0) {
                            currentPosition.toFloat()
                        } else 0f,
                        onValueChange = {
                            currentPosition = it.toInt()
                            onSeekCommand(currentPosition)
                        },
                        modifier = Modifier.weight(1f).height(20.dp)
                    )
                    Text(formatTime(duration))
                }

                // Player controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.weight(1f)) {
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

                    // Volume control for Desktop and Wasm
                    if (showVolumeControl) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Slider(
                            value = volume,
                            onValueChange = {
                                volume = it
                                setVolume(it)
                            },
                            modifier = Modifier.width(80.dp).height(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// Helper function to format time in mm:ss format
private fun formatTime(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return "0:00"
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
expect fun VideoPlayer(modifier: Modifier)

// Platform-specific functions for player control
expect fun getCurrentPosition(): Int
expect fun getVolume(): Float
expect fun setVolume(volume: Float)
expect fun isDesktopOrWasm(): Boolean
