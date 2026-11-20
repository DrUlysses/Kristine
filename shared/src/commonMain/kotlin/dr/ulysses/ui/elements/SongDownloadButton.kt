package dr.ulysses.ui.elements

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongDownloader
import dr.ulysses.entities.canDownloadSongs
import dr.ulysses.models.MainViewModel

/**
 * For a song whose file is missing: downloads it, shows that it's downloading, and offers a retry after a failure.
 */
@Composable
fun SongDownloadButton(song: Song) {
    if (song.state == Song.State.NotDownloaded) {
        DownloadButton(
            key = song.path,
            icon = Icons.Filled.Download,
            description = "Download",
            onClick = { SongDownloader.download(song) }
        )
    }
}

/**
 * Starts a download with [onClick], then shows its [SongDownloader.status] under [key]: running, or failed with
 * [onClick] to try again. Nothing where this device can't download.
 */
@Composable
fun DownloadButton(
    key: String,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    // A server's library is changed on the server, not here
    if (!canDownloadSongs || MainViewModel.isRemote) {
        return
    }
    val status by SongDownloader.status.collectAsState()
    when (status[key]) {
        SongDownloader.Status.Running -> CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier
                .padding(12.dp)
                .size(24.dp)
        )

        is SongDownloader.Status.Failed -> IconButton(
            onClick = onClick
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Download failed, try again",
                tint = MaterialTheme.colorScheme.error
            )
        }

        null -> IconButton(
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description
            )
        }
    }
}
