package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.next
import kristine.composeapp.generated.resources.play_pause
import kristine.composeapp.generated.resources.previous
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerButtons(
    isPlaying: Boolean,
    onPlayOrPauseCommand: () -> Unit,
    onPreviousCommand: () -> Unit,
    onNextCommand: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onPreviousCommand,
            content = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.previous)
                )
            }
        )
        IconButton(
            onClick = onPlayOrPauseCommand,
            content = {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(Res.string.play_pause)
                )
            }
        )
        IconButton(
            onClick = onNextCommand,
            content = {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = stringResource(Res.string.next)
                )
            }
        )
    }
}
