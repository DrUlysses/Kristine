package dr.ulysses.api.external

import dr.ulysses.Logger
import dr.ulysses.api.internal.LibraryClient
import dr.ulysses.api.internal.isRemote
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import dr.ulysses.entities.Song
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
data class MetadataCandidate(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationSeconds: Int?,
    val source: String,
    val trackNumber: Int? = null,
    /**
     * AcoustID's confidence that the audio is this recording, 0..1
     */
    val soundMatch: Double? = null,
    val score: Double = 0.0,
)

/**
 * Audio fingerprint from Chromaprint, or null where the file can't be decoded (WMA, Opus) and in the browser.
 */
@Serializable
data class AudioFingerprint(
    val durationSeconds: Int,
    val fingerprint: String,
)

expect suspend fun audioFingerprint(path: String): AudioFingerprint?

/**
 * [audioFingerprint] of a song of the connected server's library, which has the file, or of this device's.
 */
private suspend fun fingerprintOf(path: String): AudioFingerprint? = if (isRemote) {
    LibraryClient.fingerprint(path)
} else {
    audioFingerprint(path)
}

/**
 * Chromaprint's uncompressed fingerprint, one 32-bit value per ~0.12 s of sound, of the first two minutes; null where
 * [audioFingerprint] is.
 */
expect suspend fun rawFingerprint(path: String): IntArray?

/**
 * Finds metadata for a song whose tags may be empty or wrong, the way Picard and beets do:
 * - text guesses (tags, file name in both `Title - Artist` orders, cleaned of noise) asked from several providers
 * - the album's track list when artist and album are known, matched by track number and length
 * - the sound itself via a Chromaprint fingerprint and AcoustID, for files whose names say nothing
 * Every result is ranked by title/artist similarity, sound match, track number and duration.
 */
object MetadataSearch {
    private data class Guess(
        val title: String,
        val artist: String,
    )

    // "(6).mp3", "06 - Title.mp3", "6. Title.mp3": the position on the album
    private val leadingNumber = Regex("""^\(?(\d{1,3})\)?(?:[\s.\-_]|$)""")

    // Half the words in common, e.g. "Some Title" and "Some Title (Live)"
    private const val MIN_TITLE_SIMILARITY = 0.5

    private val noiseInBrackets = Regex(
        pattern = """\s*[(\[][^)\]]*(official|video|audio|lyric|remaster|feat|ft\.|\bhd\b|\bhq\b|\d{3,4}p|edition|bonus|deluxe)[^)\]]*[)\]]""",
        option = RegexOption.IGNORE_CASE
    )
    private val numberOnly = Regex("""^\(?\d+\)?$""") // "(6)": no title at all
    private val copyMarker = Regex("""\s*\(\d+\)$""") // "Song (1)" from duplicate downloads
    private val trackNumber = Regex("""^\d{1,3}[\s.\-_]+""")
    private val featuring = Regex(
        pattern = """\s+(feat\.?|ft\.?|featuring)\s.*$""",
        option = RegexOption.IGNORE_CASE
    )

    private val NOT_THE_SONGS_ALBUM = setOf(
        "Compilation",
        "Live",
        "DJ-mix",
        "Mixtape/Street",
        "Remix"
    )

    /**
     * Best match first, at most 3. [library] holds sorted songs, ranked like the catalogs' answers.
     */
    suspend fun find(
        song: Song,
        library: List<MetadataCandidate> = emptyList(),
    ): List<MetadataCandidate> = coroutineScope {
        val guesses = song.guesses()
        val trackHint = trackNumberHint(song)
        val queries = guesses.map { "${it.artist} ${it.title}".trim() }.filter { it.isNotBlank() }.distinct().take(3)
        val artist = knownArtist(song)
        val album = song.album?.takeUnless {
            it.isBlank() || it.equals(
                other = "Unknown album",
                ignoreCase = true
            )
        }

        val lookups = buildList<suspend () -> List<MetadataCandidate>> {
            queries.forEach { query -> listOf(::deezer, ::itunes).forEach { add { it(query) } } }
            if (artist != null && album != null) {
                add {
                    deezerAlbumTracks(
                        artist = artist,
                        album = album
                    )
                }
                add {
                    itunesAlbumTracks(
                        artist = artist,
                        album = album
                    )
                }
            }
            add { bySound(song) }
            // Without a title the length alone would rank random songs; the same sound counts anyway
            add { library.filter { guesses.isNotEmpty() || it.soundMatch != null } }
        }
        lookups.map { lookup ->
            async {
                runCatching { lookup() }
                    .onFailure { currentCoroutineContext().ensureActive() }
                    .onFailure { error ->
                        Logger.d(error) { "Metadata lookup failed for ${song.path}" }
                    }
                    .getOrDefault(emptyList())
            }
        }.awaitAll()
            .asSequence()
            .flatten()
            .map {
                it.copy(
                    score = score(
                        candidate = it,
                        guesses = guesses,
                        song = song,
                        trackHint = trackHint
                    )
                )
            }
            // With a title to go by, a candidate must have that title: the album's other tracks share artist, album
            // and often length, and would win for a track the catalog doesn't have, like a bonus track.
            // AcoustID knows the recording by its sound, so its answers count whatever their title
            .filter { candidate ->
                guesses.isEmpty() || candidate.soundMatch != null || guesses.any { guess ->
                    similarity(
                        a = candidate.title,
                        b = guess.title
                    ) >= MIN_TITLE_SIMILARITY
                }
            }
            .sortedByDescending { it.score }
            .distinctBy { normalize(it.title) to normalize(it.artist) }
            .take(3)
            .toList()
    }

    /**
     * [songs] as candidates from [source]; they bring no artwork, so a sorted song keeps its own. [soundMatches] are
     * the songs that sound the same, by path, ranked like AcoustID's answers.
     */
    fun candidates(
        songs: List<Song>,
        source: String,
        soundMatches: Map<String, Double> = emptyMap(),
    ) = songs.map {
        MetadataCandidate(
            title = it.title,
            artist = it.artist,
            album = it.album,
            artworkUrl = null,
            durationSeconds = it.duration,
            source = source,
            soundMatch = soundMatches[it.path]
        )
    }

    suspend fun downloadArtwork(
        url: String,
    ): ByteArray = http.get(url).also {
        check(it.status.isSuccess()) { "Artwork download failed: ${it.status}" }
    }.bodyAsBytes()

    private fun knownArtist(
        song: Song,
    ) = song.artist.takeUnless {
        it.isBlank() || it.equals(
            other = "Unknown artist",
            ignoreCase = true
        )
    }

    private fun Song.guesses(): List<Guess> = buildList {
        val tagArtist = knownArtist(this@guesses)?.let(::clean).orEmpty()
        if (title.isNotBlank()) {
            add(
                Guess(
                    title = clean(title),
                    artist = tagArtist
                )
            )
        }
        val name = clean(fileName)
        val parts = name.split(
            " - ",
            limit = 2
        ).map { it.trim() }
        if (parts.size == 2) {
            add(
                Guess(
                    title = parts[0],
                    artist = parts[1]
                )
            )
            add(
                Guess(
                    title = parts[1],
                    artist = parts[0]
                )
            )
        } else {
            add(
                Guess(
                    title = name,
                    artist = tagArtist
                )
            )
        }
    }.filter { it.title.isNotBlank() }

    private fun trackNumberHint(song: Song) =
        leadingNumber.find(song.fileName.trim())?.groupValues?.get(1)?.toIntOrNull()

    /**
     * [text] without what search engines stumble on: track numbers, "(Official Video)", "feat. …", underscores.
     */
    internal fun clean(text: String) = text.replace(
        oldChar = '_',
        newChar = ' '
    ).replace(
        regex = noiseInBrackets,
        replacement = ""
    ).replace(
        regex = numberOnly,
        replacement = ""
    ).replace(
        regex = copyMarker,
        replacement = ""
    ).replace(
        regex = trackNumber,
        replacement = ""
    ).replace(
        regex = featuring,
        replacement = ""
    ).replace(
        regex = Regex("\\s+"),
        replacement = " "
    ).trim()

    /**
     * Lower case letters, digits and single spaces only, for comparing names written differently.
     */
    internal fun normalize(
        text: String?,
    ) = text.orEmpty().lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(
        regex = Regex("\\s+"),
        replacement = " "
    ).trim()

    private fun tokens(
        text: String?,
    ) = normalize(text).split(' ').filter { it.isNotEmpty() }.toSet()

    // Dice coefficient over words: 1 for the same words, 0 for none in common
    private fun similarity(
        a: String?,
        b: String?,
    ): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0
        }
        return 2.0 * ta.intersect(tb).size / (ta.size + tb.size)
    }

    private fun score(
        candidate: MetadataCandidate,
        guesses: List<Guess>,
        song: Song,
        trackHint: Int?,
    ): Double {
        val text = guesses.maxOfOrNull { guess ->
            if (guess.artist.isBlank()) {
                similarity(
                    a = "${candidate.artist} ${candidate.title}",
                    b = guess.title
                )
            } else {
                0.6 * similarity(
                    a = candidate.title,
                    b = guess.title
                ) + 0.4 * similarity(
                    a = candidate.artist,
                    b = guess.artist
                )
            }
        } ?: 0.0
        // Album track lists have no title to compare when the title is unknown; the album and position decide
        val albumMatch = 0.2 * similarity(
            a = candidate.album,
            b = song.album
        ) + if (trackHint != null && candidate.trackNumber == trackHint) {
            0.4
        } else {
            0.0
        }
        // The file's length is the one fact the tags can't get wrong
        val duration = song.duration
        val lengthMatch = if (duration != null && duration > 0 && candidate.durationSeconds != null) {
            val difference = abs(duration - candidate.durationSeconds)
            if (difference > 30) {
                -0.3
            } else {
                0.2 * (1 - difference / 30.0)
            }
        } else {
            0.0
        }
        val sound = 1.5 * (candidate.soundMatch ?: 0.0)
        return text + albumMatch + lengthMatch + sound
    }

    private suspend fun deezer(
        query: String,
    ): List<MetadataCandidate> = getJson<DeezerTracks>("https://api.deezer.com/search") {
        parameter(
            key = "q",
            value = query
        )
        parameter(
            key = "limit",
            value = 10
        )
    }.data.map {
        it.toCandidate(
            album = it.album?.title,
            artworkUrl = it.album?.coverXl
        )
    }

    private suspend fun deezerAlbumTracks(
        artist: String,
        album: String,
    ): List<MetadataCandidate> {
        val found = getJson<DeezerAlbums>("https://api.deezer.com/search/album") {
            parameter(
                key = "q",
                value = "$artist $album"
            )
            parameter(
                key = "limit",
                value = 3
            )
        }.data.maxByOrNull {
            similarity(
                a = it.title,
                b = album
            ) + similarity(
                a = it.artist?.name,
                b = artist
            )
        } ?: return emptyList()
        return getJson<DeezerTracks>(
            "https://api.deezer.com/album/${found.id}/tracks"
        ).data.map {
            it.toCandidate(
                album = found.title,
                artworkUrl = found.coverXl
            )
        }
    }

    private suspend fun itunes(
        query: String,
    ): List<MetadataCandidate> = getJson<ItunesResults>("https://itunes.apple.com/search") {
        parameter(
            key = "term",
            value = query
        )
        parameter(
            key = "entity",
            value = "song"
        )
        parameter(
            key = "limit",
            value = 10
        )
    }.results.mapNotNull { it.toCandidate() }

    private suspend fun itunesAlbumTracks(
        artist: String,
        album: String,
    ): List<MetadataCandidate> {
        val found = getJson<ItunesResults>("https://itunes.apple.com/search") {
            parameter(
                key = "term",
                value = "$artist $album"
            )
            parameter(
                key = "entity",
                value = "album"
            )
            parameter(
                key = "limit",
                value = 3
            )
        }.results.maxByOrNull {
            similarity(
                a = it.collectionName,
                b = album
            ) + similarity(
                a = it.artistName,
                b = artist
            )
        }?.collectionId ?: return emptyList()
        return getJson<ItunesResults>("https://itunes.apple.com/lookup") {
            parameter(
                key = "id",
                value = found
            )
            parameter(
                key = "entity",
                value = "song"
            )
        }.results.mapNotNull { it.toCandidate() }
    }

    /**
     * Why identifying [song] by its sound gave nothing, for when no match was found at all: the setup that is
     * missing, AcoustID's error, or that AcoustID doesn't know the recording.
     */
    suspend fun whyNoSoundMatch(song: Song): String {
        if (acoustIdKey() == null) {
            return "To identify songs by their sound, set AcoustIdApiKey in Settings: a free application key " +
                    "from acoustid.org."
        }
        if (fingerprintOf(song.path) == null) {
            return "Couldn't fingerprint this file: its format can't be decoded for that (WMA, Opus), or it can't be read."
        }
        return runCatching { bySound(song) }
            .onFailure { currentCoroutineContext().ensureActive() }
            .fold(
                onSuccess = { "AcoustID doesn't know this recording." },
                // E.g. "invalid API key" when the key is a user key: lookups need an application key
                onFailure = { "AcoustID refused the lookup: ${it.message}" }
            )
    }

    private suspend fun acoustIdKey() =
        SettingsRepository.get(SettingKey.AcoustIdApiKey)?.value?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun bySound(song: Song): List<MetadataCandidate> {
        val key = acoustIdKey() ?: return emptyList()
        val print = fingerprintOf(song.path) ?: return emptyList()
        // POST, because a fingerprint is a few kilobytes
        ACOUSTID_LIMIT.acquire()
        val response = http.submitForm(
            url = "https://api.acoustid.org/v2/lookup",
            formParameters = parameters {
                append(
                    name = "client",
                    value = key
                )
                append(
                    name = "meta",
                    value = "recordings releasegroups"
                )
                append(
                    name = "duration",
                    value = print.durationSeconds.toString()
                )
                append(
                    name = "fingerprint",
                    value = print.fingerprint
                )
            },
        )
        val body = response.bodyAsText()
        check(response.status.isSuccess()) { "AcoustID: ${response.status} $body" }
        // AcoustID often lists one song as several recordings, even in several results of about the same score, with
        // the studio album on one and compilations on another: they are one candidate here
        return json.decodeFromString<AcoustIdResponse>(body).results
            .flatMap { result -> result.recordings.filter { it.title != null }.map { result.score to it } }
            .groupBy { (_, recording) ->
                // Without spaces: "The Band" is "TheBand"
                (recording.title + recording.artists.joinToString("") { it.name }).let(::normalize).replace(" ", "")
            }
            .values
            .map { same ->
                val (score, recording) = same.maxBy { it.first }
                // The studio album rather than a compilation, live recording or soundtrack that has the song too
                val group = same.flatMap { it.second.releasegroups }
                    .groupBy { it.id }
                    .values
                    // Of the equally good: the album of the song's own artist, not one that has it as a bonus track,
                    // then the one most of its recordings are on
                    .minWithOrNull(
                        compareBy(
                            { it.first().rank },
                            { group ->
                                group.first().artists.none { it.name == recording.artists.firstOrNull()?.name }
                            },
                            { -it.size }
                        )
                    )
                    ?.first()
                    // A compilation or live album isn't the song's album: none is better
                    ?.takeIf { it.rank < 2 }
                MetadataCandidate(
                    title = recording.title!!,
                    artist = recording.artists.joinToString(", ") { it.name },
                    album = group?.title,
                    // Cover Art Archive serves the MusicBrainz release group's front cover, if it has one
                    artworkUrl = group?.let { "https://coverartarchive.org/release-group/${it.id}/front-500" },
                    durationSeconds = recording.duration?.roundToInt(),
                    source = "AcoustID",
                    soundMatch = score,
                )
            }
    }

    @Serializable
    private data class DeezerTracks(val data: List<DeezerTrack> = emptyList())

    @Serializable
    private data class DeezerTrack(
        val title: String,
        val duration: Int? = null,
        @SerialName("track_position") val trackPosition: Int? = null,
        val artist: DeezerName,
        val album: DeezerAlbum? = null,
    ) {
        fun toCandidate(
            album: String?,
            artworkUrl: String?,
        ) = MetadataCandidate(
            title = title,
            artist = artist.name,
            album = album,
            artworkUrl = artworkUrl,
            durationSeconds = duration,
            source = "Deezer",
            trackNumber = trackPosition
        )
    }

    @Serializable
    private data class DeezerAlbums(val data: List<DeezerAlbum> = emptyList())

    @Serializable
    private data class DeezerName(val name: String)

    @Serializable
    private data class DeezerAlbum(
        val id: Long = 0,
        val title: String? = null,
        @SerialName("cover_xl") val coverXl: String? = null,
        val artist: DeezerName? = null,
    )

    @Serializable
    private data class ItunesResults(val results: List<ItunesItem> = emptyList())

    @Serializable
    private data class ItunesItem(
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
            artworkUrl = artworkUrl100?.replace(
                oldValue = "100x100bb",
                newValue = "600x600bb"
            ),
            durationSeconds = trackTimeMillis?.div(1000)?.toInt(),
            source = "iTunes",
            trackNumber = trackNumber,
        )
    }

    @Serializable
    private data class AcoustIdResponse(val results: List<AcoustIdResult> = emptyList())

    @Serializable
    private data class AcoustIdResult(
        val score: Double,
        val recordings: List<AcoustIdRecording> = emptyList(),
    )

    @Serializable
    private data class AcoustIdRecording(
        val title: String? = null,
        // Seconds with a fraction, e.g. 444.346
        val duration: Double? = null,
        val artists: List<AcoustIdName> = emptyList(),
        val releasegroups: List<AcoustIdReleaseGroup> = emptyList(),
    )

    @Serializable
    private data class AcoustIdName(val name: String)

    @Serializable
    private data class AcoustIdReleaseGroup(
        val id: String,
        val title: String? = null,
        val type: String? = null,
        val secondarytypes: List<String> = emptyList(),
        val artists: List<AcoustIdName> = emptyList(),
    ) {
        @Transient
        val rank = when {
            // Some compilations are typed Album; their artist gives them away
            secondarytypes.any { it in NOT_THE_SONGS_ALBUM } || artists.any { it.name == "Various Artists" } -> 2
            type == "Album" && secondarytypes.isEmpty() -> 0
            // Singles, EPs, and soundtracks by the artist, like a game's music by its composer
            else -> 1
        }
    }
}
