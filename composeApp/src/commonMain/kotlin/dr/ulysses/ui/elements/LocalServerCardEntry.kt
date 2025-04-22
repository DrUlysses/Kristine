package dr.ulysses.ui.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LocalServerCardEntry(
    text: String = "localhost",
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier.padding(2.dp)
    ) {
        Column {
            IconButton(
                onClick = {
                    scope.launch {
                        clipboardManager.setClipEntry(text.toClipEntry())
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy to clipboard"
                )
            }
        }
        Column {
            TextField(
                value = text,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
            )
        }
    }
}

expect fun String.toClipEntry(): ClipEntry
