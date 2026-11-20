package dr.ulysses.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.ui.views.AUTO_SORT
import dr.ulysses.ui.views.MERGE_NAMES
import dr.ulysses.ui.views.OrganizeFoldersChip

/**
 * Also the title of the duplicates screen.
 */
const val RESOLVE_DUPLICATES = "Resolve duplicates"

/**
 * The unsorted songs, below entries for removing duplicates and sorting in bulk first; songs without their file apart.
 */
@Composable
fun ManageUnsortedList(
    unsortedSongs: List<Song>,
    onDuplicatesClick: () -> Unit,
    onAutoSortClick: () -> Unit,
    onMergeNamesClick: () -> Unit,
    onClick: (Song) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // The tools, before sorting by hand: each leaves fewer songs to sort
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            ToolChip(
                label = RESOLVE_DUPLICATES,
                icon = Icons.Filled.ContentCopy,
                onClick = onDuplicatesClick
            )
            ToolChip(
                label = AUTO_SORT,
                icon = Icons.Filled.AutoFixHigh,
                onClick = onAutoSortClick
            )
            ToolChip(
                label = MERGE_NAMES,
                icon = Icons.Outlined.MergeType,
                onClick = onMergeNamesClick
            )
            OrganizeFoldersChip()
        }
        // A song without its file can't be played or tagged: it waits for its file in a tab of its own
        val (toSort, missing) = unsortedSongs.partition { it.state != Song.State.NotDownloaded }
        var showsMissing by rememberSaveable { mutableStateOf(false) }
        if (missing.isEmpty()) {
            HorizontalDivider()
        } else {
            SecondaryTabRow(
                selectedTabIndex = if (showsMissing) {
                    1
                } else {
                    0
                }
            ) {
                Tab(
                    selected = !showsMissing,
                    onClick = { showsMissing = false },
                    text = { Text("To sort · ${toSort.size}") }
                )
                Tab(
                    selected = showsMissing,
                    onClick = { showsMissing = true },
                    text = { Text("Missing files · ${missing.size}") }
                )
            }
        }
        if (showsMissing && missing.isNotEmpty()) {
            Text(
                text = "Their files are gone from this device. Download one to sort it, or find its file again " +
                        "with Refresh Songs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            SongsList(
                songs = missing
            )
        } else {
            SongsList(
                songs = toSort,
                onClick = onClick
            )
        }
    }
}

@Composable
fun ToolChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}
