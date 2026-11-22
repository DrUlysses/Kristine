package dr.ulysses.api.external

import io.ktor.client.request.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * An album as Deezer lists it. [type] is album, ep, single or compile.
 */
@Serializable
data class CatalogAlbum(
    val id: Long,
    val title: String,
    @SerialName("cover_medium")
    val coverUrl: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("record_type")
    val type: String? = null,
) {
    @Transient
    val year = releaseDate?.take(4)
}

@Serializable
data class CatalogTrack(
    val title: String,
    @SerialName("track_position")
    val position: Int? = null,
)

/**
 * What an artist released and what is on an album, from Deezer, so the library can show what it lacks.
 */
object Catalog {
    // Opening an album and going back shouldn't ask Deezer again
    private val albumsByArtist = HashMap<String, List<CatalogAlbum>>()
    private val tracksByAlbum = HashMap<Long, List<CatalogTrack>>()
    private val artistNames = HashMap<String, String>()

    /**
     * Newest first; empty when Deezer doesn't know an artist of exactly that name.
     */
    suspend fun albums(artist: String): List<CatalogAlbum> = albumsByArtist.getOrPut(artist.trim().lowercase()) {
        val found = getJson<Page<DeezerArtist>>("https://api.deezer.com/search/artist") {
            parameter(
                key = "q",
                value = artist
            )
        }.data.find {
            sameName(
                a = it.name,
                b = artist
            )
        } ?: return@getOrPut emptyList()
        artistNames[artist.trim().lowercase()] = found.name
        getJson<Page<CatalogAlbum>>("https://api.deezer.com/artist/${found.id}/albums") {
            parameter(
                key = "limit",
                value = 200
            )
        }.data
            // An edition that differs only in its noise ("Deluxe", "Remastered") is the same album
            .distinctBy { key(it.title).ifEmpty { it.title } }
            .sortedByDescending { it.releaseDate }
    }

    /**
     * How Deezer spells [artist], or null when it doesn't know them.
     */
    suspend fun artistName(artist: String): String? {
        albums(artist)
        return artistNames[artist.trim().lowercase()]
    }

    /**
     * The album of that name by that artist, or null.
     */
    suspend fun album(
        artist: String,
        title: String,
    ): CatalogAlbum? = albums(artist).find {
        sameName(
            a = it.title,
            b = title
        )
    }

    suspend fun tracks(album: CatalogAlbum): List<CatalogTrack> = tracksByAlbum.getOrPut(album.id) {
        getJson<Page<CatalogTrack>>("https://api.deezer.com/album/${album.id}/tracks") {
            parameter(
                key = "limit",
                value = 500
            )
        }.data
    }

    suspend fun cover(album: CatalogAlbum): ByteArray? = album.coverUrl?.let {
        runCatching { MetadataSearch.downloadArtwork(it) }
            .onFailure { currentCoroutineContext().ensureActive() }
            .getOrNull()
    }

    /**
     * Whether [a] and [b] name the same artist, album or song, whatever their case, punctuation or edition noise.
     */
    fun sameName(
        a: String,
        b: String,
    ) = a.trim().equals(
        other = b.trim(),
        ignoreCase = true
    ) || key(a).let { it.isNotEmpty() && it == key(b) }

    // Empty for a name that is only noise or a number, like "1999"
    fun key(name: String) = MetadataSearch.normalize(MetadataSearch.clean(name))

    @Serializable
    private data class Page<T>(val data: List<T> = emptyList())

    @Serializable
    private data class DeezerArtist(
        val id: Long,
        val name: String,
    )
}
