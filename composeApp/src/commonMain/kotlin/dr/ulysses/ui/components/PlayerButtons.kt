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

@Composable
fun PlayerButtons(
    isPlaying: Boolean,
    onPlayOrPauseCommand: () -> Unit,
    onPreviousCommand: () -> Unit,
    onNextCommand: () -> Unit,
) {
    Row {
        IconButton(
            onClick = onPreviousCommand,
            content = {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Previous"
                )
            }
        )
        IconButton(
            onClick = onPlayOrPauseCommand,
            content = {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Info else Icons.Outlined.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }
        )
        IconButton(
            onClick = onNextCommand,
            content = {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Next"
                )
            }
        )
    }
}
