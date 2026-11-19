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
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongDownloader
import dr.ulysses.entities.canDownloadSongs
import dr.ulysses.models.MainViewModel

/** For a song whose file is missing: downloads it, shows that it's downloading, and offers a retry after a failure. */
@Composable
fun SongDownloadButton(song: Song) {
    // A server's song is downloaded on the server, not here
    if (song.state != Song.State.NotDownloaded || !canDownloadSongs || MainViewModel.isRemote) {
        return
    }
    val status by SongDownloader.status.collectAsState()
    when (status[song.path]) {
        SongDownloader.Status.Running -> CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier
                .padding(12.dp)
                .size(24.dp)
        )

        SongDownloader.Status.Failed -> IconButton(
            onClick = { SongDownloader.download(song) }
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Download failed, retry",
                tint = MaterialTheme.colorScheme.error
            )
        }

        null -> IconButton(
            onClick = { SongDownloader.download(song) }
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Download"
            )
        }
    }
}
