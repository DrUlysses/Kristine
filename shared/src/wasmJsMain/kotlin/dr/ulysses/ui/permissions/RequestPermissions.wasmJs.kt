package dr.ulysses.ui.permissions

import androidx.compose.runtime.Composable

@Composable
actual fun PermissionsAlert(
    permissionsGranted: Boolean,
    onPermissionsChange: (Boolean) -> Unit,
) {
}
