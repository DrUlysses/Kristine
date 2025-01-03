package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AddSong(
    modifier: Modifier = Modifier,
    onAddSong: (String, String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Box(

        ) {
//            var songTitle by remember { mutableStateOf("Track") }
//            var songArtist by remember { mutableStateOf("Unknown Artist") }

            Column {

            }
        }
    }
}
