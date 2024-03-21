package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dr.ulysses.ui.components.PlayerButtons
import dr.ulysses.entities.Player as PlayerEntity

@Composable
fun Player() {
    BottomAppBar {
        Row {
            // TODO: Icon and song info
            val song = remember { mutableStateOf(PlayerEntity.currentSong?.title ?: "No song") }
            Text(song.value)
            PlayerButtons()
        }
    }
}
