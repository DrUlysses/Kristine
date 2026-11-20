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
import dr.ulysses.ui.elements.songs
import dr.ulysses.ui.views.AlbumRef

/**
 * The albums of [library], one per album and main artist however they are spelled, so two artists' albums of the
 * same name stay apart.
 */
@Composable
fun AlbumsList(
    library: List<Song>,
    onAlbumClicked: (AlbumRef) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    initialLoad: Boolean = true,
) {
    val albums = remember(library) {
        library.filter { albumOf(it) != null }
            .groupBy { "${nameKey(albumTitleOf(it)!!)}|${nameKey(artistOf(it))}" }
            .entries
            .sortedBy { it.key }
            .map { it.value }
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
                items = albums,
                key = { "${nameKey(albumTitleOf(it.first())!!)}|${nameKey(artistOf(it.first()))}" }
            ) { inAlbum ->
                val album = AlbumRef(
                    title = mostCommon(inAlbum.mapNotNull(::albumTitleOf)),
                    artist = mostCommon(inAlbum.map(::artistOf))
                )
                ListItem(
                    leadingContent = {
                        Cover(
                            key = inAlbum.first().path
                        ) { MainViewModel.artwork(inAlbum.first().path) }
                    },
                    headlineContent = { Text(album.title) },
                    supportingContent = { Text("${album.artist} · ${songs(inAlbum.size)}") },
                    modifier = Modifier.clickable { onAlbumClicked(album) }
                )
            }
        }
        ListScrollbar(
            state = listState
        )
    }
}
