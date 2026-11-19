package dr.ulysses.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.player.Player
import dr.ulysses.player.RepeatMode
import kotlinx.coroutines.delay
import kristine.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Bottom bar of the main screen; tapping it opens the full-screen Now Playing sheet. */
@Composable
fun Player() {
    val state = Player.state
    val song = state.currentSong
    val position by rememberPosition(
        song = song,
        isPlaying = state.isPlaying
    )
    var nowPlayingOpen by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = song != null
            ) { nowPlayingOpen = true }
    ) {
        Column {
            LinearProgressIndicator(
                progress = {
                    progressOf(
                        position = position,
                        song = song
                    )
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                drawStopIndicator = {},
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            ) {
                SongArtwork(
                    song = song,
                    modifier = Modifier.size(48.dp)
                )
                Column(
                    Modifier.weight(1f).padding(
                        horizontal = 12.dp
                    )
                ) {
                    Text(
                        text = song?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (song != null) {
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = Player::onPreviousCommand
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(Res.string.previous)
                    )
                }
                FilledIconButton(
                    onClick = Player::onPlayOrPauseCommand
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = stringResource(Res.string.play_pause)
                    )
                }
                IconButton(
                    onClick = Player::onNextCommand
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(Res.string.next)
                    )
                }
            }
        }
    }

    if (nowPlayingOpen) {
        NowPlaying(
            onDismiss = { nowPlayingOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(onDismiss: () -> Unit) {
    val state = Player.state
    val song = state.currentSong
    var position by rememberPosition(
        song = song,
        isPlaying = state.isPlaying
    )
    val duration = song?.duration ?: 0
    val upNext = state.currentPlaylist.songs.drop(state.currentTrackNum + 1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                bottom = 24.dp
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                SongArtwork(
                    song = song,
                    modifier = Modifier.widthIn(
                        max = 360.dp
                    ).fillMaxWidth().aspectRatio(1f)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = song?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song != null) {
                    Text(
                        text = listOfNotNull(song.artist, song.album).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                // Dragging moves the thumb only; the player seeks once on release
                var dragged by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = dragged ?: position.toFloat().coerceIn(
                        minimumValue = 0f,
                        maximumValue = duration.toFloat()
                    ),
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    enabled = duration > 0,
                    onValueChange = { dragged = it },
                    onValueChangeFinished = {
                        dragged?.let {
                            position = it.toInt()
                            Player.onSeekCommand(it.toInt())
                        }
                        dragged = null
                    },
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = formatTime(dragged?.toInt() ?: position),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(
                        vertical = 8.dp
                    )
                ) {
                    ToggleIcon(
                        icon = Icons.Rounded.Shuffle,
                        active = state.shuffle,
                        description = stringResource(Res.string.shuffle),
                        onClick = Player::onToggleShuffleCommand,
                    )
                    IconButton(
                        onClick = Player::onPreviousCommand,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(Res.string.previous),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    FilledIconButton(
                        onClick = Player::onPlayOrPauseCommand,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = stringResource(Res.string.play_pause),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(
                        onClick = Player::onNextCommand,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(Res.string.next),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    ToggleIcon(
                        icon = if (state.repeatMode == RepeatMode.One) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        active = state.repeatMode != RepeatMode.None,
                        description = stringResource(Res.string.repeat),
                        onClick = Player::onSwitchRepeatCommand,
                    )
                }
            }

            // Android has hardware volume keys
            if (isDesktopOrWasm()) {
                item {
                    var volume by remember { mutableStateOf(getVolume()) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = volume,
                            onValueChange = {
                                volume = it
                                setVolume(it)
                            },
                            modifier = Modifier.padding(
                                start = 12.dp
                            )
                        )
                    }
                }
            }

            if (upNext.isNotEmpty()) {
                item {
                    Text(
                        text = "Up next",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(
                            top = 24.dp,
                            bottom = 8.dp
                        )
                    )
                }
            }
            itemsIndexed(upNext) { _, queued ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { Player.onPlaySongCommand(queued) }
                        .padding(
                            vertical = 6.dp
                        )
                ) {
                    SongArtwork(
                        song = queued,
                        modifier = Modifier.size(40.dp)
                    )
                    Column(
                        Modifier.weight(1f).padding(
                            start = 12.dp
                        )
                    ) {
                        Text(
                            text = queued.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = queued.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, active: Boolean, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Artwork from the song or the DB, or a note icon on a tinted square. */
@Composable
fun SongArtwork(song: Song?, modifier: Modifier = Modifier) {
    val path = song?.path
    // Keyed by path: a new song must never show the previous song's artwork while its own loads;
    // and by the bytes, so artwork edited on the same song (Manage Unsorted) shows up
    var artwork by remember(
        key1 = path,
        key2 = song?.artwork
    ) { mutableStateOf(song?.artwork ?: path?.let { artworkCache[it] }) }
    LaunchedEffect(path) {
        if (path == null || artwork != null || path in artworkCache) {
            return@LaunchedEffect
        }
        artwork = SongRepository.getArtwork(path)
        rememberArtwork(
            path = path,
            artwork = artwork
        )
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (artwork != null) {
            AsyncImage(
                // ByteArray data has no cache key of its own, so Coil would decode it on every appearance
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(artwork)
                    // Identity hash: stable while the bytes come from artworkCache, new once they are reloaded
                    .memoryCacheKey("artwork:$path:${artwork.hashCode()}")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Recently shown artwork, so queue rows and the player don't hit the DB again; null = song has none
private val artworkCache = mutableMapOf<String, ByteArray?>()

private fun rememberArtwork(path: String, artwork: ByteArray?) {
    if (artworkCache.size >= 200) {
        artworkCache.remove(artworkCache.keys.first())
    }
    artworkCache[path] = artwork
}

/** Call when a song's artwork was edited. */
internal fun forgetArtwork(path: String) {
    artworkCache.remove(path)
}

/** Playback position in seconds, polled while playing and read once when paused. */
@Composable
private fun rememberPosition(song: Song?, isPlaying: Boolean): MutableState<Int> {
    val position = remember(song?.path) { mutableStateOf(0) }
    LaunchedEffect(
        key1 = song?.path,
        key2 = isPlaying
    ) {
        if (song == null) {
            return@LaunchedEffect
        }
        do {
            position.value = getCurrentPosition()
            delay(500)
        } while (isPlaying)
    }
    return position
}

private fun progressOf(position: Int, song: Song?): Float {
    val duration = song?.duration ?: 0
    return if (duration > 0) {
        (position.toFloat() / duration).coerceIn(
            minimumValue = 0f,
            maximumValue = 1f
        )
    } else {
        0f
    }
}

// m:ss
private fun formatTime(seconds: Int): String =
    if (seconds <= 0) {
        "0:00"
    } else {
        "${seconds / 60}:${
            (seconds % 60).toString().padStart(
                length = 2,
                padChar = '0'
            )
        }"
    }

/** Playback position in seconds. */
expect fun getCurrentPosition(): Int
expect fun getVolume(): Float
expect fun setVolume(volume: Float)
expect fun isDesktopOrWasm(): Boolean
