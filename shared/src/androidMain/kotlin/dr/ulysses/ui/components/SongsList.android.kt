package dr.ulysses.ui.components

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.loadArtworkFromFile
import dr.ulysses.models.MainViewModel
import dr.ulysses.ui.elements.SongListEntry
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun SongsList(
    modifier: Modifier,
    songs: List<Song>,
    onSongsChanged: (List<Song>) -> Unit,
    onClick: (Song) -> Unit,
    rearrangeable: Boolean,
) {
    val listState = rememberSaveable(
        saver = LazyListState.Saver
    ) {
        LazyListState(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0
        )
    }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var rememberedSongs by remember { mutableStateOf(emptyList<Song>()) }
    rememberedSongs = songs

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LaunchedEffect(listState) {
            listState.scrollToItem(listState.firstVisibleItemIndex)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .overscroll(rememberOverscrollEffect()),
            content = {
                itemsIndexed(
                    items = rememberedSongs
                ) { index, song ->
                    val isDragging = draggedIndex == index
                    var image: ByteArray? by remember { mutableStateOf(song.artwork) }
                    if (image == null) {
                        LaunchedEffect(song.path) {
                            image = MainViewModel.artwork(song.path)
                            // Not stored yet: read it from the file once and keep it (NotDownloaded and server songs have no file here)
                            if (
                                image == null &&
                                song.state != Song.State.NotDownloaded &&
                                !MainViewModel.isRemote &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            ) {
                                image = loadArtworkFromFile(song.path)
                                if (image != null) {
                                    SongRepository.upsert(
                                        song.copy(
                                            artwork = image
                                        )
                                    )
                                }
                            }
                        }
                    }
                    SongListEntry(
                        image = image,
                        title = song.title,
                        artist = song.artist,
                        isAvailable = song.state != Song.State.NotDownloaded,
                        onClick = { onClick(song) },
                        modifier = if (!rearrangeable) {
                            Modifier
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(
                                    color = if (isDragging) {
                                        Color.Gray
                                    } else {
                                        Color.DarkGray
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                                .offset {
                                    if (isDragging) {
                                        IntOffset(
                                            x = dragOffset.x.roundToInt(),
                                            y = dragOffset.y.roundToInt()
                                        )
                                    } else {
                                        IntOffset(
                                            x = 0,
                                            y = 0
                                        )
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { draggedIndex = index },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += Offset(
                                                x = dragAmount.x,
                                                y = dragAmount.y
                                            )
                                        },
                                        onDragEnd = {
                                            draggedIndex?.let { currentIndex ->
                                                val newIndex =
                                                    ((dragOffset.y / 60.dp.toPx()).roundToInt() + currentIndex)
                                                        .coerceIn(
                                                            minimumValue = 0,
                                                            maximumValue = rememberedSongs.lastIndex
                                                        )
                                                onSongsChanged(rememberedSongs.toMutableList().apply {
                                                    add(
                                                        index = newIndex,
                                                        element = removeAt(currentIndex)
                                                    )
                                                })
                                            }
                                            draggedIndex = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dragOffset = Offset.Zero
                                        }
                                    )
                                }
                        }
                    )
                }
            }
        )
    }
}
