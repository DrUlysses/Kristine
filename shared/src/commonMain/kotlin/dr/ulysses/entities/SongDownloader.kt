package dr.ulysses.entities

import dr.ulysses.Logger
import dr.ulysses.api.MetadataSearch
import dr.ulysses.models.MainViewModel
import dr.ulysses.ui.views.onSongSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether this platform can run yt-dlp. */
expect val canDownloadSongs: Boolean

/**
 * Downloads the audio of the first YouTube result for [query] into the music folder, named [fileName] plus the
 * format's extension, and returns the new file's path. Fails, never touching the music folder's files, when a file
 * of that name exists there in any format.
 */
expect suspend fun downloadAudio(
    query: String,
    fileName: String,
): String

/** Gets the file of a song whose file is missing ([Song.State.NotDownloaded]) with yt-dlp. */
object SongDownloader {
    enum class Status { Running, Failed }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(emptyMap<String, Status>())

    /** By the path of the song; a song that isn't listed was never downloaded or succeeded. */
    val status: StateFlow<Map<String, Status>> = _status.asStateFlow()

    fun download(song: Song) {
        // A song that has its file must never be replaced by a download
        if (song.state != Song.State.NotDownloaded || _status.value[song.path] == Status.Running) {
            return
        }
        _status.update { it + (song.path to Status.Running) }
        scope.launch {
            val result = runCatching {
                // The old file's name, so a later scan recognizes the file; it often says more than a title like "3"
                val oldName = song.path.substringAfterLast('/').substringBeforeLast('.')
                val title = MetadataSearch.clean(song.title)
                val query = if (title.isBlank()) {
                    MetadataSearch.clean(oldName)
                } else {
                    "${MetadataSearch.clean(song.artist)} $title"
                }
                val path = downloadAudio(
                    query = "ytsearch1:$query",
                    fileName = oldName.replace(
                        regex = UNSAFE_IN_FILE_NAME,
                        replacement = "_"
                    )
                )
                SongRepository.relink(
                    oldPath = song.path,
                    newPath = path
                )
                // The file gets this entry's metadata, so a later scan keeps the entry as it is
                onSongSave(
                    song.copy(
                        path = path,
                        artwork = SongRepository.getArtwork(path)
                    )
                ).getOrThrow()
            }.onFailure { Logger.e(it) { "Downloading ${song.artist} - ${song.title} failed" } }
            _status.update {
                if (result.isSuccess) {
                    it - song.path
                } else {
                    it + (song.path to Status.Failed)
                }
            }
            MainViewModel.loadSongs()
        }
    }

    private val UNSAFE_IN_FILE_NAME = Regex("""[/\\:*?"<>|%]""")
}
