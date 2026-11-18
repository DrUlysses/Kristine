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
 * @param onPendingSaveJobsChanged The screen may only be left once these saves are done
 * @param onSongsPathChanged Whether the library has to be rescanned on leaving
 */
@Composable
fun Settings(
    onPendingSaveJobsChanged: (List<Job>) -> Unit = {},
    onSongsPathChanged: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val settingsState = remember { SettingsState(onPendingSaveJobsChanged, onSongsPathChanged) }
    LaunchedEffect(Unit) { settingsState.loadSettings() }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Every key is shown, also those without a stored value
        SettingKey.entries.forEach { key ->
            SettingField(
                key = key,
                value = settingsState.currentSettings[key].orEmpty(),
                onValueChange = { settingsState.updateSetting(key, it, scope) }
            )
        }
    }
}

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

private class SettingsState(
    private val onPendingSaveJobsChanged: (List<Job>) -> Unit,
    private val onSongsPathChanged: (Boolean) -> Unit,
) {
    var currentSettings by mutableStateOf(emptyMap<SettingKey, String>())
        private set

    private var originalSongsPath = ""
    private val pendingSaveJobs = mutableStateListOf<Job>()

    init {
        onPendingSaveJobsChanged(pendingSaveJobs)
        onSongsPathChanged(false)
    }

    fun updateSetting(key: SettingKey, value: String, scope: CoroutineScope) {
        currentSettings = currentSettings + (key to value)
        val job = scope.launch { SettingsRepository.upsert(Setting(key, value)) }
        pendingSaveJobs.add(job)
        job.invokeOnCompletion {
            pendingSaveJobs.remove(job)
            onPendingSaveJobsChanged(pendingSaveJobs)
        }
        onPendingSaveJobsChanged(pendingSaveJobs)
        if (key == SettingKey.SongsPath) onSongsPathChanged(originalSongsPath != value)
    }

    suspend fun loadSettings() {
        val settings = SettingsRepository.getAll().associate { it.key to it.value.orEmpty() }
        currentSettings = settings
        originalSongsPath = settings[SettingKey.SongsPath].orEmpty()
    }
}
