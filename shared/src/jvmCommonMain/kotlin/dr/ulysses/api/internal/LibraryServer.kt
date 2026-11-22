package dr.ulysses.api.internal

import dr.ulysses.Logger
import dr.ulysses.api.external.audioFingerprint
import dr.ulysses.entities.*
import dr.ulysses.models.MainViewModel
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

/**
 * Serves [LibraryRoutes]: each route runs the function this device's own screens use. Clients may only touch songs
 * of this library, and organizing or sorting only does what this device planned itself.
 */
internal fun Route.libraryApi() {
    serve(LibraryRoutes.SONGS) { _: Unit -> SongRepository.getAllSongs() }
    serve(LibraryRoutes.SONGS_BY_NOT_STATE) { state: Song.State ->
        SongRepository.getByNotState(state).withoutArtwork()
    }
    serve(LibraryRoutes.SONG_BY_PATH) { path: String -> SongRepository.getByPathOrNull(path) }
    serve(LibraryRoutes.SONGS_SEARCH) { query: String -> SongRepository.search(query).withoutArtwork() }
    serve(LibraryRoutes.SONG_SAVE) { request: SaveRequest ->
        val stored = requireNotNull(SongRepository.getByPathOrNull(request.song.path)) { "Not in the library" }
        // The lists' copies come without artwork; saving one without would erase the stored artwork
        MainViewModel.saveSong(
            song = request.song.copy(
                artwork = request.song.artwork ?: stored.artwork
            ),
            sort = request.sort
        )
    }
    serve(LibraryRoutes.FINGERPRINT) { path: String ->
        requireNotNull(SongRepository.getByPathOrNull(path)) { "Not in the library" }
        audioFingerprint(path)
    }
    serve(LibraryRoutes.SCAN) { _: Unit ->
        refreshSongs()
        Unit
    }

    serve(LibraryRoutes.PLAYLISTS) { _: Unit -> PlaylistRepository.getAllPlaylists() }
    serve(LibraryRoutes.PLAYLIST_SONGS) { name: String -> PlaylistRepository.getPlaylistSongs(name).withoutArtwork() }
    serve(LibraryRoutes.PLAYLIST_ARTWORK) { name: String -> PlaylistRepository.getArtwork(name) }
    serve(LibraryRoutes.PLAYLISTS_SEARCH) { query: String -> PlaylistRepository.search(query) }
    serve(LibraryRoutes.PLAYLIST_INSERT) { playlist: Playlist ->
        PlaylistRepository.insert(playlist)
    }

    serve(LibraryRoutes.ORGANIZE_PLAN) { _: Unit -> planOrganize() }
    serve(LibraryRoutes.ORGANIZE) { paths: List<String> ->
        organize(planOrganize()?.moves.orEmpty().filter { it.song.path in paths })
    }
    serve(LibraryRoutes.DUPLICATES_RESOLVE) { request: ResolveRequest ->
        val library = SongRepository.getAllSongs().mapTo(HashSet()) { it.path }
        require(request.group.songs.all { it.path in library }) { "Not in the library" }
        resolveDuplicates(
            group = request.group,
            keep = request.keep.map { request.group.recordings[it] }
        )
    }

    serve(LibraryRoutes.SETTINGS) { _: Unit -> SettingsRepository.getAll() }
    serve(LibraryRoutes.SETTING_SAVE) { setting: Setting ->
        SettingsRepository.upsert(setting)
        Unit
    }

    webSocket(LibraryRoutes.TASKS_ROUTE) {
        // Every client sees the state of the long tasks, whoever started them
        launch {
            AutoSort.state.collect { state ->
                send(
                    AutoSortUpdate(
                        state.copy(
                            matches = state.matches.map {
                                Proposal(
                                    song = it.song.copy(
                                        artwork = null
                                    ),
                                    match = it.match
                                )
                            }
                        )
                    )
                )
                // A state flow drops the states missed while waiting: hundreds of lookups a minute become a few states a second
                delay(UPDATE_INTERVAL)
            }
        }
        launch { NameMerger.progress.collect { send(MergeUpdate(it)) } }
        launch { SongDownloader.status.collect { send(DownloadsUpdate(it)) } }
        for (frame in incoming) {
            if (frame !is Frame.Text) {
                continue
            }
            // A malformed or unknown command must not drop the client
            runCatching { Json.decodeFromString<TaskCommand>(frame.readText()) }
                .onSuccess { handle(it) }
                .onFailure { Logger.e(it) { "Ignoring malformed task command" } }
        }
    }
}

private suspend fun WebSocketServerSession.handle(command: TaskCommand) {
    when (command) {
        StartAutoSortCommand -> AutoSort.start()
        is SortProposalsCommand -> AutoSort.sort(AutoSort.state.value.matches.filter { it.song.path in command.paths })

        FindDuplicatesCommand -> launch {
            val groups = findDuplicates { step, done, total ->
                outgoing.trySend(
                    frame(
                        DuplicatesProgress(
                            step = step,
                            done = done,
                            total = total
                        )
                    )
                )
            }
            send(DuplicatesFound(groups))
        }

        is MergeNamesCommand -> NameMerger.merge(
            ids = command.ids,
            names = command.names
        )

        is MergeIntoCommand -> NameMerger.mergeInto(
            songs = command.songs.mapNotNull { SongRepository.getByPathOrNull(it.path) },
            album = command.album
        )

        // This library's entry, so a client can't make it replace a file that's there
        is DownloadCommand -> SongRepository.getByPathOrNull(command.song.path)?.let(SongDownloader::download)

        is DownloadFromLinkCommand -> SongDownloader.downloadFromLink(
            link = command.link,
            artist = command.artist,
            album = command.album,
            title = command.title
        )

        RemoveMissingSongsCommand -> launch {
            SongRepository.removeNonExistingSongs().collect { send(MaintenanceUpdate(it)) }
        }

        UpdateDurationsCommand -> launch {
            SongRepository.updateSongDurations().collect { send(MaintenanceUpdate(it)) }
        }
    }
}

private suspend fun WebSocketServerSession.send(update: TaskUpdate) = send(frame(update))

private fun frame(update: TaskUpdate) = Frame.Text(Json.encodeToString(update))

private fun List<Song>.withoutArtwork() = map {
    it.copy(
        artwork = null
    )
}

/**
 * A POST route that runs [handle] on the JSON body and answers with its result as JSON.
 */
private inline fun <reified B, reified T> Route.serve(
    path: String,
    crossinline handle: suspend (B) -> T,
) = post(path) {
    val result = handle(Json.decodeFromString<B>(call.receiveText()))
    call.respondText(
        text = Json.encodeToString(result),
        contentType = ContentType.Application.Json
    )
}

private val UPDATE_INTERVAL = 500.milliseconds
