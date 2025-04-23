package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import dr.ulysses.entities.Setting
import dr.ulysses.entities.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object Settings

@Composable
fun Settings(

) {
    val scope = rememberCoroutineScope()
    var currentSettings by remember { mutableStateOf(mutableListOf<Setting>()) }
    scope.launch {
        currentSettings = SettingsRepository.getAll().toMutableList()
    }

    Column {
        currentSettings.forEach { setting ->
            Row {
                Text(
                    text = setting.key.toString(),
                )
                TextField(
                    value = setting.value.orEmpty(),
                    onValueChange = { value ->
                        currentSettings[currentSettings.indexOf(setting)] = setting.copy(value = value)
                    }
                )
            }
        }
    }
}
