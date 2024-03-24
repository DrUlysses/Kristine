package dr.ulysses.ui.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.refreshSongs
import dr.ulysses.models.PlayerService
import dr.ulysses.ui.components.ArtistsList
import dr.ulysses.ui.components.SongList
import dr.ulysses.ui.components.TabMenu
import dr.ulysses.ui.permissions.PermissionsAlert

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Main() {
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { 3 },
    )
    val permissionsGranted = remember { mutableStateOf(false) }
    val playerModel = remember { PlayerService }
    val playerState = playerModel.state
    PermissionsAlert(
        permissionsGranted = permissionsGranted.value,
        onPermissionsChange = {
            permissionsGranted.value = it
        }
    )
    Scaffold(
        topBar = {
            TabMenu()
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState,
//                    beyondBoundsPageCount = 2,
                    pageContent = { page ->
                        when (page) {
                            0 -> {
                                ArtistsList(
                                    artists = playerState.currentTrackSequence.values.map { it.artist }.distinct(),
                                    onArtistsChanged = {} // playerModel::onArtistsChanged
                                )
                            }

                            1 -> {
                                SongList(
                                    songs = playerState.currentTrackSequence.values.toList(),
                                    onSongsChanged = playerModel::onSongsChanged,
                                    onPlaySongCommand = playerModel::onPlaySongCommand,
                                )
                            }

                            else -> listOf<Song>()
                        }
                    }
                )
            }
        },
        bottomBar = {
            Player(
                currentSong = playerState.currentSong,
                isPlaying = playerState.isPlaying,
                onPreviousCommand = playerModel::onPreviousCommand,
                onNextCommand = playerModel::onNextCommand,
                onPlayOrPauseCommand = playerModel::onPlayOrPauseCommand,
            )
        }
    )
}
