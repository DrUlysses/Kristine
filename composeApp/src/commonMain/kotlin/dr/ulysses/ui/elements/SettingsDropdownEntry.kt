package dr.ulysses.ui.elements

import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsDropdownEntry(
    val icon: ImageVector? = null,
    val text: String? = null,
    val onClick: (() -> Unit)? = null,
)
