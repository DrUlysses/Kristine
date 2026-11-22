package dr.ulysses.ui.elements

import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsDropdownEntry(
    val icon: ImageVector? = null,
    val text: String? = null,
    val onClick: (() -> Unit)? = null,
    /**
     * False grays the entry out, e.g. what the connected server can't do.
     */
    val enabled: Boolean = true,
)
