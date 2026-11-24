package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.api.internal.isRemote
import dr.ulysses.entities.SongDownloader
import dr.ulysses.entities.canDownloadSongs
import dr.ulysses.ui.components.ToolChip

/**
 * Opens [DownloadLinkDialog]; shown only where songs can be downloaded, here or on the connected server.
 */
@Composable
fun DownloadFromYouTubeChip() {
    if (!canDownloadSongs && !isRemote) {
        return
    }
    var isOpen by remember { mutableStateOf(false) }
    ToolChip(
        label = "Download from YouTube",
        icon = Icons.Outlined.AddLink,
        onClick = { isOpen = true }
    )
    if (isOpen) {
        DownloadLinkDialog(
            onDismiss = { isOpen = false }
        )
    }
}

/**
 * Takes a link to a YouTube video or playlist, whose songs then download in the background as unsorted songs.
 */
@Composable
private fun DownloadLinkDialog(onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    val isValid = SongDownloader.isYouTubeLink(link)
    val failure = SongDownloader.status.collectAsState().value[link.trim()] as? SongDownloader.Status.Failed
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.AddLink,
                contentDescription = null
            )
        },
        title = { Text("Download from YouTube") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("A video's song, or every song of a playlist, is added here unsorted. The menu shows the progress.")
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Video or playlist link") },
                    singleLine = true,
                    isError = link.isNotBlank() && !isValid,
                    supportingText = if (link.isNotBlank() && !isValid) {
                        { Text("A youtube.com or youtu.be link") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (failure != null) {
                    Text(
                        text = "The last try failed: ${failure.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    SongDownloader.downloadLink(link)
                    onDismiss()
                }
            ) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
