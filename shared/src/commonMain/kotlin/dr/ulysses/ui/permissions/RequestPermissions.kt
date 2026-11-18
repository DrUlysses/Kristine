package dr.ulysses.ui.permissions

import androidx.compose.runtime.Composable

@Composable
expect fun PermissionsAlert(
    permissionsGranted: Boolean,
    onPermissionsChange: (Boolean) -> Unit,
)
