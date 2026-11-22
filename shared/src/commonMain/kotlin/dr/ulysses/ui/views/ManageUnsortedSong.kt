package dr.ulysses.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dr.ulysses.Logger
import dr.ulysses.SUPPORTED_EXTENSIONS
import dr.ulysses.api.external.MetadataCandidate
import dr.ulysses.api.external.MetadataSearch
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.player.Player
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Edits one unsorted song. Edits only change [song] (a draft held by the caller); [onSave] writes it.
 */
@Composable
fun ManageUnsortedSong(
    song: Song,
    unsortedSongs: List<Song>,
    onSongEdited: (Song) -> Unit,
    onSave: (Song) -> Unit,
    onNextSong: () -> Unit,
    onPreviousSong: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var matches by remember(song.path) { mutableStateOf(emptyList<MetadataCandidate>()) }
    var searchRun by remember(song.path) { mutableStateOf(0) }
    var noMatchReason by remember(song.path) { mutableStateOf<String?>(null) }
    val position = unsortedSongs.indexOfFirst { it.path == song.path }

    val songsPath by produceState(
        initialValue = song.path
    ) {
        value = SettingsRepository.get(SettingKey.SongsPath)?.value ?: song.path
    }

    // Keyed by path only: typing into the fields must not start a new search
    LaunchedEffect(
        key1 = song.path,
        key2 = searchRun
    ) {
        searching = true
        noMatchReason = null
        matches = MetadataSearch.find(
            song = song,
            library = MetadataSearch.candidates(
                songs = SongRepository.ownSortedSongs(),
                source = "Your library"
            )
        )
        // Only asked when nothing matched: it repeats the sound lookup to tell what went wrong
        if (matches.isEmpty()) {
            noMatchReason = MetadataSearch.whyNoSoundMatch(song)
        }
        searching = false
    }

    val imagePicker = rememberFilePickerLauncher(
        type = PickerType.Image,
        initialDirectory = songsPath
    ) { image ->
        image?.let {
            scope.launch {
                onSongEdited(
                    song.copy(
                        artwork = it.readBytes()
                    )
                )
            }
        }
    }
    val songPicker = rememberFilePickerLauncher(
        type = PickerType.File(SUPPORTED_EXTENSIONS),
        initialDirectory = songsPath
    ) { file ->
        file?.path?.let {
            onSongEdited(
                song.copy(
                    path = it
                )
            )
        }
    }

    fun apply(match: MetadataCandidate) {
        onSongEdited(
            song.copy(
                title = match.title,
                artist = match.artist,
                album = match.album ?: song.album
            )
        )
        val url = match.artworkUrl ?: return
        scope.launch {
            try {
                val artwork = MetadataSearch.downloadArtwork(url)
                onSongEdited(
                    song.copy(
                        title = match.title,
                        artist = match.artist,
                        album = match.album ?: song.album,
                        artwork = artwork
                    )
                )
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Logger.e(e) { "Artwork download failed: $url" }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BoxWithConstraints {
            val songCard = @Composable { modifier: Modifier ->
                SongCard(
                    song = song,
                    onSongEdited = onSongEdited,
                    onPickArtwork = { imagePicker.launch() },
                    onPickFile = { songPicker.launch() },
                    modifier = modifier,
                )
            }
            val matchesCard = @Composable { modifier: Modifier ->
                MatchesCard(
                    matches = matches,
                    searching = searching,
                    noMatchReason = noMatchReason,
                    onApply = ::apply,
                    onRetry = { searchRun++ },
                    modifier = modifier,
                )
            }
            if (maxWidth >= 720.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    songCard(Modifier.weight(1f))
                    matchesCard(Modifier.weight(1f))
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    songCard(Modifier.fillMaxWidth())
                    matchesCard(Modifier.fillMaxWidth())
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onPreviousSong,
                enabled = position > 0
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = null
                )
                Text("Previous")
            }
            Spacer(Modifier.weight(1f))
            if (position >= 0) {
                Text(
                    text = "${position + 1} / ${unsortedSongs.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    onSave(song)
                    onNextSong()
                }
            ) {
                Text("Save & Next")
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun SongCard(
    song: Song,
    onSongEdited: (Song) -> Unit,
    onPickArtwork: () -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier,
) {
    ElevatedCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            onClick = onPickArtwork
                        )
                ) {
                    SongArtwork(
                        song = song,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Change artwork",
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).padding(
                        start = 16.dp
                    )
                ) {
                    Text(
                        text = song.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(song.artist, song.album.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mirrors the player: pauses/resumes this song when it is the current one
                        val isCurrent = Player.state.currentSong?.path == song.path
                        FilledTonalIconButton(
                            onClick = {
                                if (isCurrent) {
                                    Player.onPlayOrPauseCommand()
                                } else {
                                    Player.onPlaySongCommand(song)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isCurrent && Player.state.isPlaying) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = "Play or pause"
                            )
                        }
                        IconButton(
                            onClick = onPickFile
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = "Choose another file"
                            )
                        }
                    }
                }
            }
            Text(
                text = song.path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedTextField(
                value = song.title,
                onValueChange = {
                    onSongEdited(
                        song.copy(
                            title = it
                        )
                    )
                },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = song.artist,
                onValueChange = {
                    onSongEdited(
                        song.copy(
                            artist = it
                        )
                    )
                },
                label = { Text("Artist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = song.album.orEmpty(),
                onValueChange = {
                    onSongEdited(
                        song.copy(
                            album = it
                        )
                    )
                },
                label = { Text("Album") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MatchesCard(
    matches: List<MetadataCandidate>,
    searching: Boolean,
    noMatchReason: String?,
    onApply: (MetadataCandidate) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    ElevatedCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Matches",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onRetry
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Search again"
                        )
                    }
                }
            }
            when {
                searching && matches.isEmpty() -> Text(
                    text = "Searching by name, album and sound…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                matches.isEmpty() -> Text(
                    text = listOfNotNull(
                        "No matches found.",
                        noMatchReason
                    ).joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> matches.forEachIndexed { index, match ->
                    MatchRow(
                        match = match,
                        best = index == 0,
                        onApply = { onApply(match) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchRow(
    match: MetadataCandidate,
    best: Boolean,
    onApply: () -> Unit,
) {
    OutlinedCard(
        onClick = onApply,
        border = if (best) {
            BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            CardDefaults.outlinedCardBorder()
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AsyncImage(
                    model = match.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                Modifier.weight(1f).padding(
                    horizontal = 12.dp
                )
            ) {
                Text(
                    text = match.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(match.artist, match.album).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        match.durationSeconds?.let {
                            "${it / 60}:${
                                (it % 60).toString().padStart(
                                    length = 2,
                                    padChar = '0'
                                )
                            }"
                        },
                        match.trackNumber?.let { "Track $it" },
                        match.soundMatch?.let { "Sound ${(it * 100).toInt()}%" },
                        match.source,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (best) {
                Text(
                    text = "Best",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        end = 8.dp
                    )
                )
            }
        }
    }
}

expect fun onSongSave(song: Song): Result<Song>
