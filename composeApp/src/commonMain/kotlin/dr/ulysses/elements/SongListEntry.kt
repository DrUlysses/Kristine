package dr.ulysses.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
@Composable
fun SongListEntry(
    image: String = "compose-multiplatform.xml",
    title: String = "Title",
    artist: String = "Unknown Artist",
    onClick: () -> Unit? = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
//            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = "Art",
            modifier = Modifier
                .padding(8.dp)
                .size(64.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = artist,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }
    }
}
