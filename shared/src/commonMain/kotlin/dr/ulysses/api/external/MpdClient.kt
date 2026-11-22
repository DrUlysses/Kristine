package dr.ulysses.api.external

import dr.ulysses.Logger
import dr.ulysses.api.internal.LibraryRoutes
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Kristine as a client of an MPD server, like `mpc`: it plays and browses MPD's library and follows MPD's one queue,
 * which every client of the server shares. Songs are MPD's, by their path in MPD's music folder.
 */
internal class MpdClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // One command at a time on the command connection; the other connection only waits for changes
    private val lock = Mutex()
    private var commands: MpdSocket? = null

    private var library: List<Song>? = null
    private var queue: List<Song> = emptyList()
    private var playState = "stop"

    /**
     * Connects and reports MPD's queue with [onWindow] whenever it changes, until the connection ends or the caller
     * is canceled.
     */
    suspend fun connect(
        address: String,
        port: Int,
        password: String?,
        onConnected: () -> Unit,
        onWindow: (QueueWindowUpdate) -> Unit,
    ) {
        val commandSocket = openMpdSocket(
            address = address,
            port = port
        )
        val idleSocket = try {
            openMpdSocket(
                address = address,
                port = port
            )
        } catch (e: Exception) {
            commandSocket.close()
            throw e
        }
        try {
            if (!password.isNullOrEmpty()) {
                commandSocket.command("password ${quote(password)}")
                idleSocket.command("password ${quote(password)}")
            }
            // Pictures in a few large chunks instead of many 8 KiB ones; servers before 0.22.4 don't know it
            runCatching { commandSocket.command("binarylimit 1048576") }.onFailure { currentCoroutineContext().ensureActive() }
            commands = commandSocket
            library = null
            queue = songs(command("playlistinfo"))
            onConnected()
            onWindow(window())
            while (true) {
                val changed = idleSocket.idle("idle player playlist database mixer").mapTo(HashSet()) { it.second }
                if ("database" in changed) {
                    library = null
                }
                if ("playlist" in changed) {
                    queue = songs(command("playlistinfo"))
                }
                onWindow(window())
            }
        } finally {
            commands = null
            commandSocket.close()
            idleSocket.close()
        }
    }

    fun send(command: WebSocketCommand) {
        scope.launch {
            try {
                play(command)
            } catch (e: Exception) {
                ensureActive()
                Logger.e(e) { "MPD: ${command::class.simpleName} failed" }
            }
        }
    }

    private suspend fun play(command: WebSocketCommand) {
        when (command) {
            // A new queue replaces MPD's, also what other clients queued
            is SetPlaylistCommand -> commandList(
                buildList {
                    add("clear")
                    command.songs.mapTo(this) { "add ${quote(it.path)}" }
                    add("play ${command.currentSongIndex}")
                }
            )

            is PlaySongCommand -> {
                val position = queue.indexOfFirst { it.path == command.song.path }
                if (position >= 0) {
                    command("play $position")
                } else {
                    commandList(
                        listOf(
                            "add ${quote(command.song.path)}",
                            "play ${queue.size}"
                        )
                    )
                }
            }

            PauseCommand -> command("pause 1")
            is SeekCommand -> command("seekcur ${command.position}")
            is SetVolumeCommand -> command("setvol ${(command.volume * 100).roundToInt()}")
            ResumeCommand -> command(
                if (playState == "stop") {
                    "play"
                } else {
                    "pause 0"
                }
            )

            NextCommand -> command("next")
            PreviousCommand -> command("previous")
        }
    }

    /**
     * Answers the [LibraryRoutes] MPD can serve, from its library and stored playlists; the others fail, so their
     * calls return nothing.
     */
    suspend fun answer(
        route: String,
        body: String,
    ): String = when (route) {
        LibraryRoutes.SONGS -> Json.encodeToString(library())

        LibraryRoutes.SONG_BY_PATH -> {
            val path = Json.decodeFromString<String>(body)
            Json.encodeToString(library().find { it.path == path })
        }

        LibraryRoutes.SONGS_SEARCH -> {
            val query = Json.decodeFromString<String>(body)
            Json.encodeToString(
                library().filter { song ->
                    listOfNotNull(song.title, song.artist, song.album).any {
                        it.contains(
                            other = query,
                            ignoreCase = true
                        )
                    }
                }
            )
        }

        LibraryRoutes.PLAYLISTS -> Json.encodeToString(playlists())

        LibraryRoutes.PLAYLISTS_SEARCH -> {
            val query = Json.decodeFromString<String>(body)
            Json.encodeToString(
                playlists().filter {
                    it.name.contains(
                        other = query,
                        ignoreCase = true
                    )
                }
            )
        }

        LibraryRoutes.PLAYLIST_SONGS -> {
            val name = Json.decodeFromString<String>(body)
            Json.encodeToString(songs(command("listplaylistinfo ${quote(name)}")))
        }

        // A stored playlist on MPD, which its other clients can play too
        LibraryRoutes.PLAYLIST_INSERT -> {
            val playlist = Json.decodeFromString<Playlist>(body)
            commandList(playlist.songs.map { "playlistadd ${quote(playlist.name)} ${quote(it.path)}" })
            Json.encodeToString(Unit)
        }

        else -> throw MpdException("MPD can't answer $route")
    }

    /**
     * The song's embedded picture, else the cover file in its folder; null when it has neither.
     */
    suspend fun artwork(path: String): ByteArray? = picture(
        command = "readpicture",
        path = path
    ) ?: picture(
        command = "albumart",
        path = path
    )

    private suspend fun picture(
        command: String,
        path: String,
    ): ByteArray? {
        var data = ByteArray(0)
        do {
            val (pairs, bytes) = try {
                lock.withLock { connected().binary("$command ${quote(path)} ${data.size}") }
            } catch (_: MpdException) {
                // No such picture
                return null
            }
            val size = pairs.toMap()["size"]?.toIntOrNull() ?: return null
            if (bytes.isEmpty()) {
                return null
            }
            data += bytes
        } while (data.size < size)
        return data
    }

    private suspend fun window(): QueueWindowUpdate {
        val status = command("status").toMap()
        playState = status["state"] ?: "stop"
        val position = status["song"]?.toIntOrNull()
        return QueueWindowUpdate(
            current = position?.let(queue::getOrNull),
            next = status["nextsong"]?.toIntOrNull()?.let(queue::getOrNull),
            previous = position?.let { queue.getOrNull(it - 1) },
            isPlaying = playState == "play",
            queue = queue,
            position = status["elapsed"]?.toDoubleOrNull()?.toInt(),
            // -1 where MPD has no mixer to set
            volume = status["volume"]?.toIntOrNull()?.takeIf { it >= 0 }?.let { it / 100f }
        )
    }

    private suspend fun library() = library ?: songs(command("listallinfo")).also { library = it }

    private suspend fun playlists() =
        command("listplaylists").filter { it.first == "playlist" }.map { Playlist(name = it.second) }

    private suspend fun command(line: String) = lock.withLock { connected().command(line) }

    // One answer for all of [lines], which MPD runs in order and stops at the first failing one
    private suspend fun commandList(lines: List<String>) = command(
        lines.joinToString(
            separator = "\n",
            prefix = "command_list_begin\n",
            postfix = "\ncommand_list_end"
        )
    )

    private fun connected() = commands ?: throw MpdException("Not connected")

    /**
     * The songs of an answer: each starts with its `file`; the first value of a tag counts, e.g. of several artists.
     */
    private fun songs(pairs: List<Pair<String, String>>): List<Song> {
        val songs = mutableListOf<Song>()
        var tags = mutableMapOf<String, String>()
        fun add() {
            tags["file"]?.let { file ->
                songs += Song(
                    path = file,
                    title = tags["Title"] ?: file.substringAfterLast('/').substringBeforeLast('.'),
                    artist = tags["Artist"] ?: tags["AlbumArtist"] ?: "Unknown artist",
                    album = tags["Album"],
                    duration = tags["Time"]?.toIntOrNull() ?: tags["duration"]?.toDoubleOrNull()?.toInt()
                )
            }
        }
        for ((key, value) in pairs) {
            // Folders and playlists in a listing end the song before them too
            if (key == "file" || key == "directory" || key == "playlist") {
                add()
                tags = mutableMapOf()
            }
            if (key !in tags) {
                tags[key] = value
            }
        }
        add()
        return songs
    }

    private fun quote(argument: String) = "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
