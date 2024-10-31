package dr.ulysses.ui.views

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastDistinctBy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dr.ulysses.entities.Song
import dr.ulysses.models.PlayerService
import dr.ulysses.ui.components.AlbumsList
import dr.ulysses.ui.components.ArtistsList
import dr.ulysses.ui.components.SongList
import dr.ulysses.ui.components.TabMenu
import dr.ulysses.ui.permissions.PermissionsAlert

@Composable
fun Main() {
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { 3 },
    )
    val permissionsGranted = remember { mutableStateOf(false) }
    val playerModel = remember { PlayerService }
    val playerState = playerModel.state
    var topBarText by remember { mutableStateOf<String?>(null) }
    val navBarController = rememberNavController()
    PermissionsAlert(
        permissionsGranted = permissionsGranted.value,
        onPermissionsChange = {
            permissionsGranted.value = it
        }
    )
    Scaffold(
        topBar = {
            TabMenu(
                pagerState = pagerState,
                topText = topBarText,
                navigateUp = {
                    navBarController.navigateUp()
                    topBarText = null
                }
            )
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
                    beyondViewportPageCount = 10,
                    pageContent = {
                        when (pagerState.currentPage) {
                            0 -> {
                                NavHost(
                                    navController = navBarController,
                                    startDestination = "artists",
                                    enterTransition = { fadeIn(animationSpec = tween(300)) },
                                    exitTransition = { fadeOut(animationSpec = tween(300)) },
                                ) {
                                    composable("artists") {
                                        ArtistsList(
                                            artists = playerState
                                                .currentTrackSequence
                                                .values
                                                .map { it.artist }
                                                .fastDistinctBy(String::lowercase),
                                            onArtistsChanged = {},
                                            onArtistClicked = { artist ->
                                                topBarText = artist
                                                navBarController.navigate("artist")
                                            }
                                        )
                                    }
                                    composable("artist") {
                                        SongList(
                                            songs = playerState.currentTrackSequence.values.filter {
                                                topBarText != null &&
                                                        it.artist.lowercase() == topBarText!!.lowercase()
                                            },
                                            onSongsChanged = playerModel::onSongsChanged,
                                            onPlaySongCommand = playerModel::onPlaySongCommand,
                                        )
                                    }
                                }
                            }

                            1 -> {
                                SongList(
                                    songs = playerState.currentTrackSequence.values.toList(),
                                    onSongsChanged = playerModel::onSongsChanged,
                                    onPlaySongCommand = playerModel::onPlaySongCommand,
                                )
                            }

                            2 -> {
                                NavHost(
                                    navController = navBarController,
                                    startDestination = "albums",
                                ) {
                                    composable("albums") {
                                        AlbumsList(
                                            albums = playerState.currentTrackSequence.values.mapNotNull { it.album }
                                                .fastDistinctBy(String::lowercase),
                                            onAlbumsChanged = {},
                                            onAlbumClicked = { album ->
                                                topBarText = album
                                                navBarController.navigate("album")
                                            }
                                        )
                                    }
                                    composable("album") {
                                        SongList(
                                            songs = playerState.currentTrackSequence.values.filter {
                                                it.album != null && topBarText != null &&
                                                        it.album.lowercase() == topBarText!!.lowercase()
                                            },
                                            onSongsChanged = playerModel::onSongsChanged,
                                            onPlaySongCommand = playerModel::onPlaySongCommand,
                                        )
                                    }
                                }
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
