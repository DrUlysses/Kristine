package dr.ulysses.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dr.ulysses.entities.*
import dr.ulysses.models.MainViewModel
import dr.ulysses.ui.elements.Cover
import dr.ulysses.ui.elements.ListScrollbar
import dr.ulysses.ui.elements.count
import dr.ulysses.ui.elements.songs

/**
 * The main artists of [library] (see [artistOf]), each with how many albums and songs of theirs the library has.
 */
@Composable
fun ArtistsList(
    library: List<Song>,
    onArtistClicked: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    initialLoad: Boolean = true,
) {
    val artists = remember(library) {
        library.groupBy { nameKey(artistOf(it)) }.entries.sortedBy { it.key }.map { it.value }
    }
    LaunchedEffect(Unit) {
        if (initialLoad) {
            listState.scrollToItem(0)
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = artists,
                key = { nameKey(artistOf(it.first())) }
            ) { bySong ->
                val artist = mostCommon(bySong.map(::artistOf))
                val albums = bySong.mapNotNull { albumTitleOf(it)?.let(::nameKey) }.distinct().size
                ListItem(
                    leadingContent = {
                        Cover(
                            key = bySong.first().path
                        ) { MainViewModel.artwork(bySong.first().path) }
                    },
                    headlineContent = { Text(artist) },
                    supportingContent = {
                        Text(
                            "${
                                count(
                                    number = albums,
                                    one = "album",
                                    many = "albums"
                                )
                            } · ${songs(bySong.size)}"
                        )
                    },
                    modifier = Modifier.clickable { onArtistClicked(artist) }
                )
            }
        }
        ListScrollbar(
            state = listState
        )
    }
}
