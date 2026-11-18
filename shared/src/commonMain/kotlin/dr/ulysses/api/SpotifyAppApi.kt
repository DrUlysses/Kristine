package dr.ulysses.api

/**
 * Data class to hold Spotify search results
 */
data class SpotifySearchResult(
    val title: String,
    val album: String,
    val artist: String,
    val artworkUrl: String? = null,
)

/**
 * Service class to handle Spotify API integration
 * This is a common interface that will be implemented differently on each platform
 */
expect object SpotifyAppApi {
    /**
     * Initialize the Spotify API with client ID and client secret
     */
    suspend fun initialize(): Any?

    /**
     * Search for a track on Spotify
     */
    suspend fun searchTrack(
        query: String,
    ): Result<List<SpotifySearchResult>>

    /**
     * Save Spotify credentials to settings
     */
    suspend fun saveCredentials(clientId: String, clientSecret: String): Result<Unit>
}
