package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardTab
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dr.ulysses.models.RepeatMode
import kristine.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerButtons(
    isPlaying: Boolean,
    onPlayOrPauseCommand: () -> Unit,
    onPreviousCommand: () -> Unit,
    onNextCommand: () -> Unit,
    onToggleShuffleCommand: () -> Unit,
    isShuffling: Boolean = false,
    onSwitchRepeatCommand: () -> Unit,
    repeatMode: RepeatMode = RepeatMode.None,
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
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = stringResource(Res.string.next)
                )
            }
        )
        Spacer(
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggleShuffleCommand,
            content = {
                Icon(
                    imageVector = if (isShuffling) Icons.Outlined.Shuffle else Icons.AutoMirrored.Outlined.TrendingFlat,
                    contentDescription = stringResource(Res.string.shuffle)
                )
            }
        )
        IconButton(
            onClick = onSwitchRepeatCommand,
            content = {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.None -> Icons.AutoMirrored.Outlined.KeyboardTab
                        RepeatMode.One -> Icons.Outlined.RepeatOne
                        RepeatMode.All -> Icons.Outlined.Repeat
                    },
                    contentDescription = stringResource(Res.string.repeat)
                )
            }
        )
    }
}
