package dr.ulysses.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dr.ulysses.entities.Song

/** Also the title of the duplicates screen. */
const val RESOLVE_DUPLICATES = "Resolve duplicates"

/** The unsorted songs, below an entry for removing duplicates first, which leaves fewer songs to sort. */
@Composable
fun ManageUnsortedList(
    unsortedSongs: List<Song>,
    onDuplicatesClick: () -> Unit,
    onClick: (Song) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ListItem(
            headlineContent = { Text(RESOLVE_DUPLICATES) },
            supportingContent = { Text("Songs that are in the library more than once: keep one copy of each") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null
                )
            },
            modifier = Modifier.clickable(onClick = onDuplicatesClick)
        )
        HorizontalDivider()
        SongsList(
            songs = unsortedSongs,
            onClick = onClick
        )
    }
}
