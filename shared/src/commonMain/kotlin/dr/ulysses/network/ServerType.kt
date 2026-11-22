package dr.ulysses.network

import kotlinx.serialization.Serializable

/**
 * A server connected to before, offered again in Connections; its password, if any, isn't kept.
 */
@Serializable
data class RecentServer(
    val address: String,
    val port: Int,
    val type: ServerType,
)

/**
 * The kinds of server Kristine connects to, and what each can do; the UI disables what the connected one can't.
 */
enum class ServerType(
    /**
     * Its library can be managed: songs edited, sorted, merged, organized, downloaded.
     */
    val canManageLibrary: Boolean,
    val canPlayHere: Boolean,
) {
    Kristine(
        canManageLibrary = true,
        canPlayHere = true
    ),

    /**
     * A Music Player Daemon, controlled like `mpc` does: it plays and browses, but can't change its files.
     */
    Mpd(
        canManageLibrary = false,
        canPlayHere = false
    ),
}
