package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.ui.components.SongList
import dr.ulysses.ui.components.TabMenu
import dr.ulysses.ui.permissions.PermissionsAlert

@Composable
fun Main() {
    val permissionsGranted = remember { mutableStateOf(false) }
    PermissionsAlert()
    Scaffold(
        topBar = {
            TabMenu()
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SongList()
        }
    }
}
