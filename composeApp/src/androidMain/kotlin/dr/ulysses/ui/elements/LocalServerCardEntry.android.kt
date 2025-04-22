package dr.ulysses.ui.elements

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun String.toClipEntry(): ClipEntry = ClipEntry(ClipData.newPlainText("text", this))
