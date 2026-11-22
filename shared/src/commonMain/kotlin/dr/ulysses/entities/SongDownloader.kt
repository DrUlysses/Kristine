package dr.ulysses.entities

import dr.ulysses.Logger
import dr.ulysses.api.external.Catalog
import dr.ulysses.api.external.CatalogAlbum
import dr.ulysses.api.external.MetadataSearch
import dr.ulysses.api.internal.DownloadCommand
import dr.ulysses.api.internal.DownloadFromLinkCommand
import dr.ulysses.api.internal.LibraryTasks
import dr.ulysses.api.internal.isRemote
import dr.ulysses.models.MainViewModel
import dr.ulysses.ui.views.onSongSave
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Whether this platform can run yt-dlp.
 */
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

/**
 * Gets the file of a song whose file is missing ([Song.State.NotDownloaded]) with yt-dlp.
 */
object SongDownloader {
    @Serializable
    sealed interface Status {
        @Serializable
        data object Running : Status

        @Serializable
        data class Failed(val reason: String) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val localStatus = MutableStateFlow(emptyMap<String, Status>())

    /**
     * By the path of the song, or by [linkKey] for a download from a link; what isn't listed never ran or succeeded.
     */
    val status: StateFlow<Map<String, Status>> = LibraryTasks.localOrRemote(
        local = localStatus,
        remote = LibraryTasks.downloads
    )

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "music.youtube.com",
        "youtu.be"
    )

    private val UNSAFE_IN_FILE_NAME = Regex("""[/\\:*?"<>|%]""")

    fun download(song: Song) {
        if (isRemote) {
            LibraryTasks.send(DownloadCommand(song))
            return
        }
        // A song that has its file must never be replaced by a download
        if (song.state != Song.State.NotDownloaded || localStatus.value[song.path] == Status.Running) {
            return
        }
        localStatus.update { it + (song.path to Status.Running) }
        scope.launch {
            val result = runCatching {
                val title = MetadataSearch.clean(song.title)
                val query = if (title.isBlank()) {
                    MetadataSearch.clean(song.fileName)
                } else {
                    "${MetadataSearch.clean(song.artist)} $title"
                }
                val path = downloadAudio(
                    query = "ytsearch1:$query",
                    // The old file's name, so a later scan recognizes the file; it often says more than a title like "3"
                    fileName = song.fileName.replace(
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
            localStatus.update {
                if (result.isSuccess) {
                    it - song.path
                } else {
                    it + (song.path to Status.Failed(result.exceptionOrNull()?.message.orEmpty()))
                }
            }
            MainViewModel.loadSongs()
        }
    }

    /**
     * Identifies a download of a track of [album] in [status].
     */
    fun linkKey(
        album: CatalogAlbum,
        title: String,
    ) = "${album.id}|${Catalog.key(title)}"

    /**
     * Whether yt-dlp may be given [text]: a YouTube page, never an option or a file.
     */
    fun isYouTubeLink(text: String): Boolean {
        val url = parseUrl(text.trim()) ?: return false
        return url.protocol.isSecure() && url.host.removePrefix("www.").removePrefix("m.") in YOUTUBE_HOSTS
    }

    /**
     * Gets a track of [album] that the library lacks from a YouTube [link] the user found, and adds it as a sorted
     * song with the album's tags and cover.
     */
    fun downloadFromLink(
        link: String,
        artist: String,
        album: CatalogAlbum,
        title: String,
    ) {
        if (isRemote) {
            LibraryTasks.send(
                DownloadFromLinkCommand(
                    link = link,
                    artist = artist,
                    album = album,
                    title = title
                )
            )
            return
        }
        val key = linkKey(
            album = album,
            title = title
        )
        if (!isYouTubeLink(link) || localStatus.value[key] == Status.Running) {
            return
        }
        localStatus.update { it + (key to Status.Running) }
        scope.launch {
            val result = runCatching {
                val path = downloadAudio(
                    query = link.trim(),
                    fileName = "$artist - $title".replace(
                        regex = UNSAFE_IN_FILE_NAME,
                        replacement = "_"
                    )
                )
                val song = inLibrarySpelling(
                    song = Song(
                        path = path,
                        title = title,
                        album = album.title,
                        artist = artist,
                        artwork = Catalog.cover(album),
                        duration = getDurationFromMetadata(path),
                        state = Song.State.Sorted
                    ),
                    library = SongRepository.getAllSongs()
                )
                // Tagged and filed in its Artist / Album folder like any sorted song
                MainViewModel.saveSong(song)
            }.onFailure { Logger.e(it) { "Downloading $artist - $title from $link failed" } }
            localStatus.update {
                if (result.isSuccess) {
                    it - key
                } else {
                    it + (key to Status.Failed(result.exceptionOrNull()?.message.orEmpty()))
                }
            }
            MainViewModel.loadSongs()
        }
    }
}
