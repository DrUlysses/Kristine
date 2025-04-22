package dr.ulysses.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.ui.elements.SettingsDropdownEntry
import kotlinx.coroutines.launch

@Composable
fun SettingsDropdown(
    menuEntries: List<SettingsDropdownEntry> = emptyList(),
    menuExpanded: MutableState<Boolean>,
) {
    val scope = rememberCoroutineScope()
    DropdownMenu(
        expanded = menuExpanded.value,
        onDismissRequest = { menuExpanded.value = false },
        modifier = Modifier.fillMaxWidth(0.5f)
    ) {
        for (menuEntry in menuEntries) {
            DropdownMenuItem(
                onClick = {
                    menuEntry.onClick?.let { onClick ->
                        scope.launch {
                            onClick()
                        }
                    }
                    menuExpanded.value = false
                },
                leadingIcon = {
                    menuEntry.icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                        )
                    }
                },
                text = {
                    Text(
                        text = menuEntry.text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                contentPadding = PaddingValues(8.dp)
            )
        }
    }
}
