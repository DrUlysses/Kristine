package dr.ulysses.ui.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource

@Composable
fun SongListEntry(
    modifier: Modifier = Modifier,
    image: ByteArray? = null,
    title: String = "Title",
    artist: String = "Unknown Artist",
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = image?.let {
                runCatching {
                    BitmapPainter(it.decodeToImageBitmap())
                }.getOrNull()
            } ?: painterResource(Res.drawable.compose_multiplatform),
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
