package dr.ulysses.ui.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kristine.shared.generated.resources.Res
import kristine.shared.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource

/**
 * @param isAvailable False for a song whose file is missing: dimmed and not clickable
 * @param trailing At the end of the row, never dimmed, e.g. a [SongDownloadButton]
 */
@Composable
fun SongListEntry(
    modifier: Modifier = Modifier,
    image: ByteArray? = null,
    title: String = "Title",
    artist: String = "Unknown Artist",
    isAvailable: Boolean = true,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = isAvailable,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .alpha(
                    if (isAvailable) {
                        1f
                    } else {
                        0.4f
                    }
                )
        ) {
            SongInfo(
                image = image,
                title = title,
                artist = artist
            )
        }
        trailing()
    }
}

@Composable
private fun RowScope.SongInfo(
    image: ByteArray?,
    title: String,
    artist: String,
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
