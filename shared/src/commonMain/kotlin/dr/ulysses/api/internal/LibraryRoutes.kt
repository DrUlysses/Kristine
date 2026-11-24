package dr.ulysses.api.internal

import dr.ulysses.entities.DuplicateGroup
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable

/**
 * Kristine's library API: a connected client manages the server's library through these routes, which run the
 * same functions the server's own screens use. Requests and answers are JSON; long tasks go over [TASKS_ROUTE].
 */
internal object LibraryRoutes {
    const val SONGS = "/api/songs"
    const val SONGS_BY_NOT_STATE = "/api/songs/by-not-state"
    const val SONG_BY_PATH = "/api/songs/by-path"
    const val SONGS_SEARCH = "/api/songs/search"
    const val SONG_SAVE = "/api/songs/save"
    const val FINGERPRINT = "/api/songs/fingerprint"
    const val SAME_SOUND = "/api/songs/same-sound"
    const val RAW_FINGERPRINT = "/api/songs/raw-fingerprint"
    const val SCAN = "/api/scan"

    const val PLAYLISTS = "/api/playlists"
    const val PLAYLIST_SONGS = "/api/playlists/songs"
    const val PLAYLIST_ARTWORK = "/api/playlists/artwork"
    const val PLAYLISTS_SEARCH = "/api/playlists/search"
    const val PLAYLIST_SAVE = "/api/playlists/save"
    const val PLAYLIST_DELETE = "/api/playlists/delete"

    const val ORGANIZE_PLAN = "/api/organize/plan"
    const val ORGANIZE = "/api/organize"
    const val DUPLICATES_RESOLVE = "/api/duplicates/resolve"

    const val SETTINGS = "/api/settings"
    const val SETTING_SAVE = "/api/settings/save"

    /**
     * The WebSocket for long tasks: [TaskCommand]s in, [TaskUpdate]s out.
     */
    const val TASKS_ROUTE = "/api/tasks"
}

/**
 * A playlist to store in place of the one named [oldName], or as a new one when that's null.
 */
@Serializable
internal data class PlaylistSaveRequest(
    val playlist: Playlist,
    val oldName: String?,
)

@Serializable
internal data class SaveRequest(
    val song: Song,
    val sort: Boolean,
)

/**
 * The recordings of [group] to keep, by their index, since the server's copies are other objects.
 */
@Serializable
internal data class ResolveRequest(
    val group: DuplicateGroup,
    val keep: List<Int>,
)
