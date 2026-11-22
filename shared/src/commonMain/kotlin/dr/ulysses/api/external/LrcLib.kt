package dr.ulysses.api.external

import dr.ulysses.entities.Song
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * lrclib.net: free lyrics, most of them with timestamps, without an API key.
 */
internal object LrcLib {
    @Serializable
    private data class Record(
        val duration: Double? = null,
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null,
    ) {
        val lyrics = syncedLyrics?.takeIf { it.isNotBlank() } ?: plainLyrics?.takeIf { it.isNotBlank() }
    }

    /**
     * Synced lyrics, else plain ones, or null when LRCLIB doesn't know the song; throws when it can't be reached.
     */
    suspend fun find(song: Song): String? {
        // The exact song first: title, artist, album and length within a few seconds
        val exact = get("https://lrclib.net/api/get") {
            parameter(
                key = "track_name",
                value = song.title
            )
            parameter(
                key = "artist_name",
                value = song.artist
            )
            song.album?.let {
                parameter(
                    key = "album_name",
                    value = it
                )
            }
            song.duration?.let {
                parameter(
                    key = "duration",
                    value = it
                )
            }
        }
        exact?.let { return json.decodeFromString<Record>(it).lyrics }
        // Another album or a slightly different length: the closest length, synced ones first
        val found = get("https://lrclib.net/api/search") {
            parameter(
                key = "track_name",
                value = song.title
            )
            parameter(
                key = "artist_name",
                value = song.artist
            )
        }?.let { json.decodeFromString<List<Record>>(it) }.orEmpty()
        val duration = song.duration
        return found.filter { duration == null || it.duration == null || abs(it.duration - duration) <= 5 }
            .sortedBy { it.syncedLyrics.isNullOrBlank() }
            .firstNotNullOfOrNull { it.lyrics }
    }

    /**
     * The body, or null for "not found".
     */
    private suspend fun get(
        url: String,
        block: HttpRequestBuilder.() -> Unit,
    ): String? {
        val response = http.get(url) {
            // LRCLIB asks clients to name themselves
            header(
                key = HttpHeaders.UserAgent,
                value = "Kristine music player"
            )
            block()
        }
        if (response.status == HttpStatusCode.NotFound) {
            return null
        }
        check(response.status.isSuccess()) { "$url: ${response.status}" }
        return response.bodyAsText()
    }
}
