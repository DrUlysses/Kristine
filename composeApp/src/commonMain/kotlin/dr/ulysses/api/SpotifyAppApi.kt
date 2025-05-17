package dr.ulysses.api

import com.adamratzman.spotify.SpotifyAppApi
import com.adamratzman.spotify.spotifyAppApi
import dr.ulysses.Logger
import dr.ulysses.entities.Setting
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 */
object SpotifyAppApi {
    private var api: SpotifyAppApi? = null

    /**
     * Initialize the Spotify API with client ID and client secret
     */
    suspend fun initialize(): SpotifyAppApi? = withContext(Dispatchers.Default) {
        if (api == null) {
            api = runCatching {
                val clientId = SettingsRepository.get(SettingKey.SpotifyClientId)?.value
                val clientSecret = SettingsRepository.get(SettingKey.SpotifyClientSecret)?.value

                if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
                    Logger.e { "Spotify client ID or client secret not set" }
                    return@withContext null
                }

                spotifyAppApi(clientId, clientSecret).build()
            }.onFailure { e ->
                Logger.e { "Failed to initialize Spotify API: ${e.message}" }
            }.getOrNull()
        }
        return@withContext api
    }

    /**
     * Search for a track on Spotify
     */
    suspend fun searchTrack(
        query: String,
    ): Result<List<SpotifySearchResult>> = withContext(Dispatchers.Default) {
        initialize()?.let { spotifyApi ->
            val searchResults = spotifyApi.search.searchTrack(query, limit = 5).items

            val results = searchResults.map { track ->
                SpotifySearchResult(
                    title = track.name,
                    album = track.album.name,
                    artist = track.artists.firstOrNull()?.name ?: "Unknown Artist",
                    artworkUrl = track.album.images?.firstOrNull()?.url
                )
            }
            Result.success(results)
        } ?: Result.failure(Exception("Spotify API is unavailable"))
    }

    /**
     * Save Spotify credentials to settings
     */
    suspend fun saveCredentials(clientId: String, clientSecret: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                SettingsRepository.upsert(Setting(SettingKey.SpotifyClientId, clientId))
                SettingsRepository.upsert(Setting(SettingKey.SpotifyClientSecret, clientSecret))
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e { "Failed to save Spotify credentials: ${e.message}" }
                Result.failure(e)
            }
        }

    // Note: In a real implementation, we would need a platform-specific way to download images
    // For now, we'll just use the URL directly in the UI
}
