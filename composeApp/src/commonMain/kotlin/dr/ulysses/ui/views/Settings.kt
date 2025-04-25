package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import dr.ulysses.entities.Setting
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object Settings

@Composable
fun Settings(

) {
    val scope = rememberCoroutineScope()
    var currentSettings by remember { mutableStateOf(mutableMapOf<SettingKey, String>()) }
    LaunchedEffect(Unit) {
        currentSettings = SettingsRepository.getAll().associate { it.key to it.value.orEmpty() }.toMutableMap()
    }

    Column {
        currentSettings.forEach { (key, value) ->
            Row {
                var settingText by mutableStateOf(value)
                Text(text = key.toString())
                TextField(
                    value = settingText,
                    onValueChange = { newValue ->
                        settingText = newValue
                        currentSettings[key] = newValue
                        scope.launch {
                            SettingsRepository.upsert(Setting(key, newValue))
                        }
                    }
                )
            }
        }
    }
}
