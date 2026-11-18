package dr.ulysses.api

import dr.ulysses.Logger
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import dr.ulysses.entities.Song
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

data class MetadataCandidate(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationSeconds: Int?,
    val source: String,
    val trackNumber: Int? = null,
    /** AcoustID's confidence that the audio is this recording, 0..1 */
    val soundMatch: Double? = null,
    val score: Double = 0.0,
)

/** Audio fingerprint from Chromaprint, or null where `fpcalc` isn't available. */
class AudioFingerprint(val durationSeconds: Int, val fingerprint: String)

expect suspend fun audioFingerprint(path: String): AudioFingerprint?

/**
 * Finds metadata for a song whose tags may be empty or wrong, the way Picard and beets do:
 * - text guesses (tags, file name in both `Title - Artist` orders, cleaned of noise) asked from several providers
 * - the album's track list when artist and album are known, matched by track number and length
 * - the sound itself via a Chromaprint fingerprint and AcoustID, for files whose names say nothing
 * Every result is ranked by title/artist similarity, sound match, track number and duration.
 */
object MetadataSearch {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private class Guess(val title: String, val artist: String)

    /** Best match first, at most 3. */
    suspend fun find(song: Song): List<MetadataCandidate> = coroutineScope {
        val guesses = guesses(song)
        val trackHint = trackNumberHint(song)
        val queries = guesses.map { "${it.artist} ${it.title}".trim() }.filter { it.isNotBlank() }.distinct().take(3)
        val artist = knownArtist(song)
        val album = song.album?.takeUnless { it.isBlank() || it.equals("Unknown album", ignoreCase = true) }

        val lookups = buildList<suspend () -> List<MetadataCandidate>> {
            queries.forEach { query -> listOf(::spotify, ::deezer, ::itunes).forEach { add { it(query) } } }
            if (artist != null && album != null) {
                add { deezerAlbumTracks(artist, album) }
                add { itunesAlbumTracks(artist, album) }
            }
            add { bySound(song) }
        }
        lookups.map { lookup ->
            async {
                runCatching { lookup() }
                    .onFailure { error ->
                        Logger.d(error) { "Metadata lookup failed for ${song.path}" }
                    }
                    .getOrDefault(emptyList())
            }
        }.awaitAll()
            .asSequence()
            .flatten()
            .map { it.copy(score = score(it, guesses, song, trackHint)) }
            .sortedByDescending { it.score }
            .distinctBy { normalize(it.title) to normalize(it.artist) }
            .take(3)
            .toList()
    }

    suspend fun downloadArtwork(url: String): ByteArray = client.get(url).also {
        check(it.status.isSuccess()) { "Artwork download failed: ${it.status}" }
    }.bodyAsBytes()

    private fun knownArtist(song: Song) =
        song.artist.takeUnless { it.isBlank() || it.equals("Unknown artist", ignoreCase = true) }

    private fun fileName(song: Song) = song.path.substringAfterLast('/').substringBeforeLast('.')

    private fun guesses(song: Song): List<Guess> = buildList {
        val tagArtist = knownArtist(song)?.let(::clean).orEmpty()
        if (song.title.isNotBlank()) add(Guess(clean(song.title), tagArtist))
        val name = clean(fileName(song))
        val parts = name.split(" - ", limit = 2).map { it.trim() }
        if (parts.size == 2) {
            add(Guess(title = parts[0], artist = parts[1]))
            add(Guess(title = parts[1], artist = parts[0]))
        } else {
            add(Guess(title = name, artist = tagArtist))
        }
    }.filter { it.title.isNotBlank() }

    // "(6).mp3", "06 - Title.mp3", "6. Title.mp3": the position on the album
    private val leadingNumber = Regex("""^\(?(\d{1,3})\)?(?:[\s.\-_]|$)""")

    private fun trackNumberHint(song: Song) =
        leadingNumber.find(fileName(song).trim())?.groupValues?.get(1)?.toIntOrNull()

    private val noiseInBrackets = Regex(
        """\s*[(\[][^)\]]*(official|video|audio|lyric|remaster|feat|ft\.|\bhd\b|\bhq\b|\d{3,4}p)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val numberOnly = Regex("""^\(?\d+\)?$""") // "(6)": no title at all
    private val copyMarker = Regex("""\s*\(\d+\)$""") // "Song (1)" from duplicate downloads
    private val trackNumber = Regex("""^\d{1,3}[\s.\-_]+""")
    private val featuring = Regex("""\s+(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)

    private fun clean(text: String) = text.replace('_', ' ')
        .replace(noiseInBrackets, "")
        .replace(numberOnly, "")
        .replace(copyMarker, "")
        .replace(trackNumber, "")
        .replace(featuring, "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalize(text: String?) =
        text.orEmpty().lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(Regex("\\s+"), " ").trim()

    private fun tokens(text: String?) = normalize(text).split(' ').filter { it.isNotEmpty() }.toSet()

    // Dice coefficient over words: 1 for the same words, 0 for none in common
    private fun similarity(a: String?, b: String?): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return 2.0 * ta.intersect(tb).size / (ta.size + tb.size)
    }

    private fun score(candidate: MetadataCandidate, guesses: List<Guess>, song: Song, trackHint: Int?): Double {
        val text = guesses.maxOfOrNull { guess ->
            if (guess.artist.isBlank()) similarity("${candidate.artist} ${candidate.title}", guess.title)
            else 0.6 * similarity(candidate.title, guess.title) + 0.4 * similarity(candidate.artist, guess.artist)
        } ?: 0.0
        // Album track lists have no title to compare when the title is unknown; the album and position decide
        val albumMatch = 0.2 * similarity(candidate.album, song.album) +
                if (trackHint != null && candidate.trackNumber == trackHint) 0.4 else 0.0
        // The file's length is the one fact the tags can't get wrong
        val duration = song.duration
        val lengthMatch = if (duration != null && duration > 0 && candidate.durationSeconds != null) {
            val difference = abs(duration - candidate.durationSeconds)
            if (difference > 30) -0.3 else 0.2 * (1 - difference / 30.0)
        } else 0.0
        val sound = 1.5 * (candidate.soundMatch ?: 0.0)
        return text + albumMatch + lengthMatch + sound
    }

    private suspend fun spotify(query: String) = SpotifyAppApi.searchTrack(query).getOrThrow()

    private suspend fun deezer(query: String): List<MetadataCandidate> =
        getJson<DeezerTracks>("https://api.deezer.com/search") {
            parameter("q", query)
            parameter("limit", 10)
        }.data.map { it.toCandidate(album = it.album?.title, artworkUrl = it.album?.coverXl) }

    private suspend fun deezerAlbumTracks(artist: String, album: String): List<MetadataCandidate> {
        val found = getJson<DeezerAlbums>("https://api.deezer.com/search/album") {
            parameter("q", "$artist $album")
            parameter("limit", 3)
        }.data.maxByOrNull { similarity(it.title, album) + similarity(it.artist?.name, artist) } ?: return emptyList()
        return getJson<DeezerTracks>("https://api.deezer.com/album/${found.id}/tracks").data
            .map { it.toCandidate(album = found.title, artworkUrl = found.coverXl) }
    }

    private suspend fun itunes(query: String): List<MetadataCandidate> =
        getJson<ItunesResults>("https://itunes.apple.com/search") {
            parameter("term", query)
            parameter("entity", "song")
            parameter("limit", 10)
        }.results.mapNotNull { it.toCandidate() }

    private suspend fun itunesAlbumTracks(artist: String, album: String): List<MetadataCandidate> {
        val found = getJson<ItunesResults>("https://itunes.apple.com/search") {
            parameter("term", "$artist $album")
            parameter("entity", "album")
            parameter("limit", 3)
        }.results.maxByOrNull { similarity(it.collectionName, album) + similarity(it.artistName, artist) }
            ?.collectionId ?: return emptyList()
        return getJson<ItunesResults>("https://itunes.apple.com/lookup") {
            parameter("id", found)
            parameter("entity", "song")
        }.results.mapNotNull { it.toCandidate() }
    }

    private suspend fun bySound(song: Song): List<MetadataCandidate> {
        val key =
            SettingsRepository.get(SettingKey.AcoustIdApiKey)?.value?.takeIf { it.isNotBlank() } ?: return emptyList()
        val print = audioFingerprint(song.path) ?: return emptyList()
        // POST, because a fingerprint is a few kilobytes
        val response = client.submitForm(
            url = "https://api.acoustid.org/v2/lookup",
            formParameters = parameters {
                append("client", key)
                append("meta", "recordings releasegroups")
                append("duration", print.durationSeconds.toString())
                append("fingerprint", print.fingerprint)
            },
        )
        check(response.status.isSuccess()) { "AcoustID: ${response.status} ${response.bodyAsText()}" }
        return json.decodeFromString<AcoustIdResponse>(response.bodyAsText()).results.flatMap { result ->
            result.recordings.mapNotNull { recording ->
                val group = recording.releasegroups.firstOrNull()
                MetadataCandidate(
                    title = recording.title ?: return@mapNotNull null,
                    artist = recording.artists.joinToString(", ") { it.name },
                    album = group?.title,
                    // Cover Art Archive serves the MusicBrainz release group's front cover, if it has one
                    artworkUrl = group?.let { "https://coverartarchive.org/release-group/${it.id}/front-500" },
                    durationSeconds = recording.duration,
                    source = "AcoustID",
                    soundMatch = result.score,
                )
            }
        }
    }

    private suspend inline fun <reified T> getJson(
        url: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = client.get(url) { block() }
        check(response.status.isSuccess()) { "$url: ${response.status}" }
        return json.decodeFromString(response.bodyAsText())
    }

    @Serializable
    private class DeezerTracks(val data: List<DeezerTrack> = emptyList())

    @Serializable
    private class DeezerTrack(
        val title: String,
        val duration: Int? = null,
        @SerialName("track_position") val trackPosition: Int? = null,
        val artist: DeezerName,
        val album: DeezerAlbum? = null,
    ) {
        fun toCandidate(album: String?, artworkUrl: String?) =
            MetadataCandidate(title, artist.name, album, artworkUrl, duration, "Deezer", trackNumber = trackPosition)
    }

    @Serializable
    private class DeezerAlbums(val data: List<DeezerAlbum> = emptyList())

    @Serializable
    private class DeezerName(val name: String)

    @Serializable
    private class DeezerAlbum(
        val id: Long = 0,
        val title: String? = null,
        @SerialName("cover_xl") val coverXl: String? = null,
        val artist: DeezerName? = null,
    )

    @Serializable
    private class ItunesResults(val results: List<ItunesItem> = emptyList())

    @Serializable
    private class ItunesItem(
        val trackName: String? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val collectionId: Long? = null,
        val artworkUrl100: String? = null,
        val trackTimeMillis: Long? = null,
        val trackNumber: Int? = null,
    ) {
        // Lookups also return the album itself as the first item; only tracks become candidates
        fun toCandidate(): MetadataCandidate? = MetadataCandidate(
            title = trackName ?: return null,
            artist = artistName.orEmpty(),
            album = collectionName,
            // The API names a 100 px thumbnail; the same URL serves larger sizes
            artworkUrl = artworkUrl100?.replace("100x100bb", "600x600bb"),
            durationSeconds = trackTimeMillis?.div(1000)?.toInt(),
            source = "iTunes",
            trackNumber = trackNumber,
        )
    }

    @Serializable
    private class AcoustIdResponse(val results: List<AcoustIdResult> = emptyList())

    @Serializable
    private class AcoustIdResult(val score: Double, val recordings: List<AcoustIdRecording> = emptyList())

    @Serializable
    private class AcoustIdRecording(
        val title: String? = null,
        val duration: Int? = null,
        val artists: List<AcoustIdName> = emptyList(),
        val releasegroups: List<AcoustIdReleaseGroup> = emptyList(),
    )

    @Serializable
    private class AcoustIdName(val name: String)

    @Serializable
    private class AcoustIdReleaseGroup(val id: String, val title: String? = null)
}
