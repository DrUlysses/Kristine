package dr.ulysses.api

data class SpotifySearchResult(
    val title: String,
    val album: String,
    val artist: String,
    val artworkUrl: String? = null,
)

/** Track search used to fill in metadata of unsorted songs. Credentials come from the settings. */
expect object SpotifyAppApi {
    suspend fun searchTrack(query: String): Result<List<SpotifySearchResult>>
}
