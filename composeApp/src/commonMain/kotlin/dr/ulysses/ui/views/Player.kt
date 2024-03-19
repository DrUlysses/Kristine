package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dr.ulysses.ui.components.PlayerButtons
import dr.ulysses.entities.Player as PlayerEntity

@Composable
fun Player() {
    BottomAppBar {
        Row {
            // TODO: Icon and song info
            Text(PlayerEntity.currentSong?.artist ?: "Unknown Artist")
            PlayerButtons()
        }
    }
}
