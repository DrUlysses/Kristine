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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Settings screen that displays and allows editing of all application settings.
 *
 * @param onPendingSaveJobsChanged Callback that provides the current list of pending save jobs
 * @param onSongsPathChanged Callback that indicates if the SongsPath setting has changed
 */
@Composable
fun Settings(
    onPendingSaveJobsChanged: (List<Job>) -> Unit = {},
    onSongsPathChanged: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    // State for settings and tracking changes
    val settingsState = rememberSettingsState(
        onPendingSaveJobsChanged = onPendingSaveJobsChanged, onSongsPathChanged = onSongsPathChanged
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Display all settings, including those that might not have values yet
        SettingKey.entries.forEach { key ->
            SettingField(
                key = key, value = settingsState.currentSettings[key] ?: "", onValueChange = { newValue ->
                    // Update the setting value
                    settingsState.updateSetting(key, newValue, scope)
                })
        }
    }
}

/**
 * Composable for a single setting field.
 */
@Composable
private fun SettingField(
    key: SettingKey,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var textFieldValue by remember(value) { mutableStateOf(value) }

    Row(
        verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
    ) {
        TextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onValueChange(newValue)
            },
            label = { Text(key.toString()) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Class to hold and manage settings state.
 */
private class SettingsState(
    initialSettings: Map<SettingKey, String> = emptyMap(),
    private val onPendingSaveJobsChanged: (List<Job>) -> Unit,
    private val onSongsPathChanged: (Boolean) -> Unit,
) {
    // Current settings values
    var currentSettings by mutableStateOf(initialSettings.toMutableMap())
        private set

    // Original SongsPath value to detect changes
    private var originalSongsPath: String = initialSettings[SettingKey.SongsPath] ?: ""

    // List of pending save operations
    private val pendingSaveJobs = mutableStateListOf<Job>()

    init {
        // Initial notification of empty pending jobs
        onPendingSaveJobsChanged(pendingSaveJobs)
        // Initial notification of SongsPath change status
        onSongsPathChanged(false)
    }

    /**
     * Update a setting value and save it to the repository.
     */
    fun updateSetting(key: SettingKey, value: String, scope: CoroutineScope) {
        // Update the in-memory value
        currentSettings[key] = value

        // Launch a coroutine to save the setting
        val job = scope.launch {
            SettingsRepository.upsert(Setting(key, value))
        }

        // Add the job to the list of pending jobs
        pendingSaveJobs.add(job)

        // Remove the job from the list when it completes
        job.invokeOnCompletion {
            pendingSaveJobs.remove(job)
            onPendingSaveJobsChanged(pendingSaveJobs)
        }

        // Notify about pending jobs
        onPendingSaveJobsChanged(pendingSaveJobs)

        // Check if SongsPath changed and notify
        if (key == SettingKey.SongsPath) {
            onSongsPathChanged(originalSongsPath != value)
        }
    }

    /**
     * Load settings from the repository.
     */
    suspend fun loadSettings() {
        // Load existing settings
        val settings = SettingsRepository.getAll().associate { it.key to it.value.orEmpty() }

        // Ensure all required settings are included
        val mergedSettings = SettingKey.entries.associateWith { "" }.toMutableMap().apply {
            putAll(settings)
        }

        // Update current settings
        currentSettings = mergedSettings

        // Store original SongsPath for comparison
        originalSongsPath = settings[SettingKey.SongsPath] ?: ""
    }
}

/**
 * Remember a SettingsState and load settings when created.
 */
@Composable
private fun rememberSettingsState(
    onPendingSaveJobsChanged: (List<Job>) -> Unit,
    onSongsPathChanged: (Boolean) -> Unit,
): SettingsState {
    val settingsState = remember {
        SettingsState(
            onPendingSaveJobsChanged = onPendingSaveJobsChanged, onSongsPathChanged = onSongsPathChanged
        )
    }

    // Load settings when the composable is first created
    LaunchedEffect(Unit) {
        settingsState.loadSettings()
    }

    return settingsState
}
