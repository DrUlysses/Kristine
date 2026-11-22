package dr.ulysses.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dr.ulysses.api.internal.LibraryClient
import dr.ulysses.entities.Setting
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import dr.ulysses.entities.musicFolder
import dr.ulysses.models.MainViewModel
import dr.ulysses.network.NetworkManager
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * This device's settings and, while connected, the server's below them; a row opens a dialog that edits it.
 *
 * @param onPendingSaveJobsChanged The screen may only be left once these saves are done
 */
@Composable
fun Settings(
    onPendingSaveJobsChanged: (List<Job>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val isConnected by NetworkManager.isConnected.collectAsState()
    val serverType by NetworkManager.serverType.collectAsState()
    val server by NetworkManager.currentServer.collectAsState()
    val pendingSaves = remember { mutableStateListOf<Job>() }
    var device by remember { mutableStateOf(emptyMap<SettingKey, String>()) }
    var remote by remember { mutableStateOf<Map<SettingKey, String>?>(null) }
    var editing by remember { mutableStateOf<Editing?>(null) }

    LaunchedEffect(Unit) {
        onPendingSaveJobsChanged(pendingSaves)
        device = SettingsRepository.getAll().toValues()
    }
    // Only a Kristine server has settings to edit
    LaunchedEffect(
        key1 = isConnected,
        key2 = serverType
    ) {
        remote = if (isConnected && serverType.canManageLibrary) {
            LibraryClient.settings().toValues()
        } else {
            null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SectionHeader(
                title = "This device",
                note = null
            )
        }
        items(DEVICE_KEYS) { key ->
            SettingRow(
                key = key,
                value = device[key].orEmpty(),
                onClick = {
                    editing = Editing(
                        key = key,
                        onServer = false
                    )
                }
            )
        }
        val serverValues = remote
        if (serverValues != null) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(
                        vertical = 8.dp
                    )
                )
                SectionHeader(
                    title = "Server",
                    note = "Changes apply to ${server?.first ?: "the server"} and its library"
                )
            }
            items(SERVER_KEYS) { key ->
                SettingRow(
                    key = key,
                    value = serverValues[key].orEmpty(),
                    onClick = {
                        editing = Editing(
                            key = key,
                            onServer = true
                        )
                    }
                )
            }
        }
    }

    editing?.let { edit ->
        SettingDialog(
            key = edit.key,
            value = if (edit.onServer) {
                remote?.get(edit.key)
            } else {
                device[edit.key]
            }.orEmpty(),
            // A picker only knows this device's files
            canBrowse = !edit.onServer,
            onDismiss = { editing = null },
            onSave = { value ->
                editing = null
                if (edit.onServer) {
                    remote = remote?.plus(edit.key to value)
                } else {
                    device = device + (edit.key to value)
                }
                val job = scope.launch {
                    val setting = Setting(
                        key = edit.key,
                        value = value
                    )
                    if (edit.onServer) {
                        LibraryClient.saveSetting(setting)
                    } else {
                        SettingsRepository.upsert(setting)
                    }
                    // The changed folder's library is scanned again, in the background
                    if (edit.key == SettingKey.SongsPath) {
                        MainViewModel.scanLibrary(
                            onServer = edit.onServer
                        )
                    }
                }
                pendingSaves.add(job)
                job.invokeOnCompletion { pendingSaves.remove(job) }
            }
        )
    }
}

/**
 * The setting being edited, of the server or of this device.
 */
private data class Editing(
    val key: SettingKey,
    val onServer: Boolean,
)

@Composable
private fun SectionHeader(
    title: String,
    note: String?,
) {
    Column(
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 8.dp
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingRow(
    key: SettingKey,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label(key)) },
        supportingContent = {
            Text(
                text = when {
                    value.isBlank() -> "Not set"
                    isSecret(key) -> "••••••••"
                    else -> value
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier.clickable(
            onClick = onClick
        )
    )
}

@Composable
private fun SettingDialog(
    key: SettingKey,
    value: String,
    canBrowse: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    // The browser has no files to pick
    val hasFiles by produceState(
        initialValue = false
    ) {
        this.value = canBrowse && musicFolder() != null
    }
    val folderPicker = rememberDirectoryPickerLauncher(
        initialDirectory = text.ifBlank { null }
    ) { folder ->
        folder?.path?.let { text = it }
    }
    val filePicker = rememberFilePickerLauncher(
        initialDirectory = text.substringBeforeLast('/').ifBlank { null }
    ) { file ->
        file?.path?.let { text = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label(key)) },
        text = {
            Column {
                Text(
                    text = description(key),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        bottom = 16.dp
                    )
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label(key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasFiles && key in FOLDER_KEYS) {
                    TextButton(
                        onClick = { folderPicker.launch() }
                    ) {
                        Text("Choose folder")
                    }
                }
                if (hasFiles && key in FILE_KEYS) {
                    TextButton(
                        onClick = { filePicker.launch() }
                    ) {
                        Text("Choose file")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim()) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun List<Setting>.toValues() = associate { it.key to it.value.orEmpty() }

private val FOLDER_KEYS = setOf(
    SettingKey.SongsPath,
    SettingKey.PlaylistPath
)

private val FILE_KEYS = setOf(SettingKey.YtDlpPath)

// Kept by the app itself, not edited here
private val DEVICE_KEYS = SettingKey.entries - SettingKey.RecentServers

// Which server to connect to is this device's choice
private val SERVER_KEYS = DEVICE_KEYS - SettingKey.DefaultServerAddress

private fun label(key: SettingKey) = when (key) {
    SettingKey.SongsPath -> "Music folder"
    SettingKey.PlaylistPath -> "Playlist folder"
    SettingKey.DefaultServerAddress -> "Default server"
    SettingKey.AcoustIdApiKey -> "AcoustID key"
    SettingKey.YtDlpPath -> "yt-dlp program"
    SettingKey.RecentServers -> "Recent servers"
}

private fun description(key: SettingKey) = when (key) {
    SettingKey.SongsPath -> "Where the library's songs are. Sorted songs are filed here; empty uses the music folder in Kristine's own folder."
    SettingKey.PlaylistPath -> "Where playlists are kept."
    SettingKey.DefaultServerAddress -> "The server to connect to."
    SettingKey.AcoustIdApiKey -> "Identifies songs by their sound. A free application key from acoustid.org."
    SettingKey.YtDlpPath -> "Downloads missing songs. Empty uses yt-dlp from the PATH."
    // Never listed
    SettingKey.RecentServers -> ""
}

private fun isSecret(key: SettingKey) = key == SettingKey.AcoustIdApiKey
