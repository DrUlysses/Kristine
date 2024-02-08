package dr.ulysses.ui.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun requestPermissions(): Boolean {
    val permissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    )

    if (permissionsState.allPermissionsGranted) {
        Text("Read Media permissions Granted")
    } else {
        Column {
            val allPermissionsRevoked =
                permissionsState.permissions.size ==
                        permissionsState.revokedPermissions.size

            val textToShow = if (!allPermissionsRevoked)
                "Allow remaining permissions to continue."
            else if (permissionsState.shouldShowRationale)
            // Both location permissions have been denied
                "Allow permissions to continue."
            else
            // First time the user sees this feature or the user doesn't want to be asked again
                "This feature requires read media permission"

            val buttonText = if (!allPermissionsRevoked)
                "Allow to read media"
            else
                "Request permissions"

            Text(text = textToShow)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                Text(buttonText)
            }
        }
    }

    return permissionsState.allPermissionsGranted
}
