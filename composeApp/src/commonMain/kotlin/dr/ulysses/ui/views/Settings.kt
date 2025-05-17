package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Setting
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object Settings

@Composable
fun Settings() {
    val scope = rememberCoroutineScope()
    var currentSettings by remember { mutableStateOf(mutableMapOf<SettingKey, String>()) }

    // Track the original SongsPath value to detect changes
    var originalSongsPath by remember { mutableStateOf("") }

    // Ensure all required settings are included
    val requiredSettings = remember {
        SettingKey.entries.associateWith { "" }.toMutableMap()
    }

    LaunchedEffect(Unit) {
        // Load existing settings
        val settings = SettingsRepository.getAll().associate { it.key to it.value.orEmpty() }

        // Merge with required settings to ensure all keys are present
        val mergedSettings = requiredSettings.toMutableMap().apply {
            putAll(settings)
        }

        currentSettings = mergedSettings.toMutableMap()

        // Store original SongsPath for comparison
        originalSongsPath = settings[SettingKey.SongsPath] ?: ""
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Display all settings, including those that might not have values yet
        SettingKey.entries.forEach { key ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                var settingText by remember(currentSettings[key]) {
                    mutableStateOf(currentSettings[key] ?: "")
                }

                TextField(
                    value = settingText,
                    onValueChange = { newValue ->
                        settingText = newValue
                        currentSettings[key] = newValue
                        scope.launch {
                            SettingsRepository.upsert(Setting(key, newValue))
                        }
                    },
                    label = { Text(key.toString()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Store whether SongsPath was changed in a CompositionLocal for access by the FAB
    CompositionLocalProvider(
        LocalSongsPathChanged provides (originalSongsPath != currentSettings[SettingKey.SongsPath])
    ) {
        // Content is provided by parent composable
    }
}

// CompositionLocal to track if SongsPath was changed
val LocalSongsPathChanged = compositionLocalOf { false }
