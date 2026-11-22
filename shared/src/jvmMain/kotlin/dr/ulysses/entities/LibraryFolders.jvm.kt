package dr.ulysses.entities

import dr.ulysses.network.appDataDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual suspend fun musicFolder(): String? = songsRoot()?.toString()

actual suspend fun moveSongFile(
    from: String,
    to: String,
): Boolean = withContext(Dispatchers.IO) {
    val source = Path(localFilePath(from))
    val target = Path(localFilePath(to))
    val moved = runCatching {
        target.parent?.let(SystemFileSystem::createDirectories)
        // A rename within the music folder; it fails across drives, and the song then stays where it is
        SystemFileSystem.atomicMove(
            source = source,
            destination = target
        )
    }.isSuccess
    // Tidying up must not turn a done move into a failed one: the entry has to follow the file
    val root = songsRoot()
    if (moved && root != null) {
        runCatching {
            removeEmptyFolders(
                from = source.parent,
                root = root
            )
        }
    }
    moved
}

/** The music folder from the settings, or "music" in Kristine's own folder. */
internal suspend fun songsFolder(): String =
    SettingsRepository.get(SettingKey.SongsPath)?.value?.takeIf { it.isNotBlank() }
        ?: Path(appDataDirectory(), "music").toString()

/** [songsFolder], resolved; null when it doesn't exist (e.g. unmounted). */
private suspend fun songsRoot(): Path? = runCatching { SystemFileSystem.resolve(Path(songsFolder())) }
    .onFailure { currentCoroutineContext().ensureActive() }
    .getOrNull()

/** Folders a move left empty, up to [root] itself, which stays. */
private fun removeEmptyFolders(
    from: Path?,
    root: Path,
) {
    var folder = from
    while (folder != null && folder != root && folder.toString().startsWith(root.toString()) &&
        SystemFileSystem.list(folder).isEmpty()
    ) {
        SystemFileSystem.delete(folder)
        folder = folder.parent
    }
}

actual suspend fun writeSongFile(
    file: String,
    bytes: ByteArray,
): String? = "file:///$file".takeIf {
    writeNewFile(
        file = file,
        bytes = bytes
    )
}
