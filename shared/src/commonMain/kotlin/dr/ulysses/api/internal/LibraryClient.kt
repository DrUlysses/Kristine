package dr.ulysses.api.internal

import dr.ulysses.Logger
import dr.ulysses.api.external.AudioFingerprint
import dr.ulysses.entities.*
import dr.ulysses.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Whether the library functions work on the connected server's library instead of this device's.
 */
// shortcut: a device that serves clients while connected to another server passes their calls on to that server;
// fine while the headless server never connects anywhere
internal val isRemote
    get() = NetworkManager.isConnected.value

/**
 * The connected server's [LibraryRoutes], one call each.
 */
internal object LibraryClient {
    suspend fun allSongs(): List<Song> = call(
        route = LibraryRoutes.SONGS,
        body = Unit,
        fallback = emptyList()
    )

    suspend fun songsByNotState(state: Song.State): List<Song> = call(
        route = LibraryRoutes.SONGS_BY_NOT_STATE,
        body = state,
        fallback = emptyList()
    )

    suspend fun songByPath(path: String): Song? = call(
        route = LibraryRoutes.SONG_BY_PATH,
        body = path,
        fallback = null
    )

    suspend fun searchSongs(query: String): List<Song> = call(
        route = LibraryRoutes.SONGS_SEARCH,
        body = query,
        fallback = emptyList()
    )

    suspend fun saveSong(
        song: Song,
        sort: Boolean,
    ): Song = call(
        route = LibraryRoutes.SONG_SAVE,
        body = SaveRequest(
            song = song,
            sort = sort
        ),
        fallback = song
    )

    suspend fun fingerprint(path: String): AudioFingerprint? = call(
        route = LibraryRoutes.FINGERPRINT,
        body = path,
        fallback = null
    )

    suspend fun scan(): Unit = call(
        route = LibraryRoutes.SCAN,
        body = Unit,
        fallback = Unit
    )

    suspend fun playlists(): List<Playlist> = call(
        route = LibraryRoutes.PLAYLISTS,
        body = Unit,
        fallback = emptyList()
    )

    suspend fun playlistSongs(name: String): List<Song> = call(
        route = LibraryRoutes.PLAYLIST_SONGS,
        body = name,
        fallback = emptyList()
    )

    suspend fun playlistArtwork(name: String): ByteArray? = call(
        route = LibraryRoutes.PLAYLIST_ARTWORK,
        body = name,
        fallback = null
    )

    suspend fun searchPlaylists(query: String): List<Playlist> = call(
        route = LibraryRoutes.PLAYLISTS_SEARCH,
        body = query,
        fallback = emptyList()
    )

    suspend fun insertPlaylist(playlist: Playlist): Unit = call(
        route = LibraryRoutes.PLAYLIST_INSERT,
        body = playlist,
        fallback = Unit
    )

    suspend fun organizePlan(): OrganizePlan? = call(
        route = LibraryRoutes.ORGANIZE_PLAN,
        body = Unit,
        fallback = null
    )

    /**
     * The server does its own plan's moves of these songs; returns the moves that failed.
     */
    suspend fun organize(moves: List<Move>): List<Move> = call(
        route = LibraryRoutes.ORGANIZE,
        body = moves.map { it.song.path },
        fallback = moves
    )

    suspend fun resolveDuplicates(
        group: DuplicateGroup,
        keep: List<Recording>,
    ): List<Song> = call(
        route = LibraryRoutes.DUPLICATES_RESOLVE,
        body = ResolveRequest(
            group = group,
            keep = keep.map { group.recordings.indexOf(it) }
        ),
        fallback = group.songs
    )

    suspend fun settings(): List<Setting> = call(
        route = LibraryRoutes.SETTINGS,
        body = Unit,
        fallback = emptyList()
    )

    suspend fun saveSetting(setting: Setting): Unit = call(
        route = LibraryRoutes.SETTING_SAVE,
        body = setting,
        fallback = Unit
    )

    /**
     * [fallback] when the call fails, e.g. because the connection dropped: nothing found, nothing done.
     */
    private suspend inline fun <reified B, reified T> call(
        route: String,
        body: B,
        fallback: T,
    ): T = try {
        // Off the UI thread: a whole library is a large answer to read and decode
        withContext(Dispatchers.Default) {
            Json.decodeFromString(
                NetworkManager.callLibrary(
                    route = route,
                    body = Json.encodeToString(body)
                )
            )
        }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Logger.e(e) { "Library call $route failed" }
        fallback
    }
}
