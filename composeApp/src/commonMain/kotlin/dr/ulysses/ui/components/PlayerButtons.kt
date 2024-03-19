package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dr.ulysses.entities.Player

@Composable
fun PlayerButtons() {
    Row {
        IconButton(
            onClick = { Player.previous() },
            content = {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Previous"
                )
            }
        )
        val isPlaying = remember { Player.playing() }
        if (isPlaying) {
            IconButton(
                onClick = { Player.pause() },
                content = {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Pause"
                    )
                }
            )
        } else {
            IconButton(
                onClick = { Player.play() },
                content = {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Play"
                    )
                }
            )
        }
        IconButton(
            onClick = { Player.next() },
            content = {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Next"
                )
            }
        )
    }
}
