package dr.ulysses.ui.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.FlingBehavior
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
import dr.ulysses.entities.Song
import dr.ulysses.ui.components.SongList
import dr.ulysses.ui.components.TabMenu
import dr.ulysses.ui.permissions.PermissionsAlert

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Main() {
    val pagerState = rememberPagerState(
        pageCount = { 3 },
    )
    val permissionsGranted = remember { mutableStateOf(false) }
    PermissionsAlert(
        permissionsGranted = permissionsGranted.value,
        onPermissionsChange = { permissionsGranted.value = it }
    )
    var songs: List<Song> by remember { mutableStateOf(emptyList()) }
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
                                SongList(
                                    songs = songs,
                                    onSongsChanged = { songs = it }
                                )
                            }

                            else -> listOf<Song>()
                        }
                    }
                )
            }
        },
        bottomBar = {
            Player()
        }
    )
}
