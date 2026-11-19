package dr.ulysses.ui.elements

import androidx.compose.ui.platform.ClipEntry

actual fun String.toClipEntry(): ClipEntry = ClipEntry.withPlainText(this)
