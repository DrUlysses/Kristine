package dr.ulysses.api

import com.adamratzman.spotify.spotifyAppApi
import dr.ulysses.Logger
import dr.ulysses.entities.Setting
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.adamratzman.spotify.SpotifyAppApi as SpotifyApi

/**
 * JVM implementation of SpotifyAppApi
 * This implementation uses the Spotify API library
 */
actual object SpotifyAppApi {
    private var api: SpotifyApi? = null

    /**
     * Initialize the Spotify API with client ID and client secret
     */
    actual suspend fun initialize(): Any? = withContext(Dispatchers.Default) {
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
    actual suspend fun searchTrack(
        query: String,
    ): Result<List<SpotifySearchResult>> = withContext(Dispatchers.Default) {
        (initialize() as? SpotifyApi)?.let { spotifyApi ->
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
    actual suspend fun saveCredentials(clientId: String, clientSecret: String): Result<Unit> =
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
}
