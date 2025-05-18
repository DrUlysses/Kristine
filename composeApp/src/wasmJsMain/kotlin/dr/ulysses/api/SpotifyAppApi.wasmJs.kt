package dr.ulysses.api

import dr.ulysses.Logger
import dr.ulysses.entities.Setting
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WASM JS implementation of SpotifyAppApi
 * This is a stub implementation that preserves the API but doesn't actually use the Spotify API library
 */
actual object SpotifyAppApi {
    /**
     * Initialize the Spotify API with client ID and client secret
     * In WASM JS, this is a stub implementation that always returns null
     */
    actual suspend fun initialize(): Any? = null

    /**
     * Search for a track on Spotify
     * In WASM JS, this is a stub implementation that returns an empty list
     */
    actual suspend fun searchTrack(
        query: String,
    ): Result<List<SpotifySearchResult>> = withContext(Dispatchers.Default) {
        Logger.d { "WASM JS: Spotify search not implemented, query was: $query" }
        Result.success(emptyList())
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
