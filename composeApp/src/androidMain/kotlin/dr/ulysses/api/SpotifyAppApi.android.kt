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
 * Android implementation of SpotifyAppApi
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
        try {
            val spotifyApi = initialize() as? SpotifyApi
            if (spotifyApi == null) {
                return@withContext Result.failure(Exception("Spotify API is unavailable"))
            }

            // Use reflection to avoid direct references to Spotify API classes
            val searchResults = spotifyApi.javaClass.getMethod("getSearch").invoke(spotifyApi)
            val searchTrackMethod =
                searchResults.javaClass.getMethod("searchTrack", String::class.java, Int::class.java)
            val searchTrackResult = searchTrackMethod.invoke(searchResults, query, 5)
            val items = searchTrackResult.javaClass.getMethod("getItems").invoke(searchTrackResult) as List<*>

            val results = items.mapNotNull { track ->
                try {
                    // Extract track information using reflection
                    val trackClass = track?.javaClass
                    val nameMethod = trackClass?.getMethod("getName")
                    val title = nameMethod?.invoke(track) as? String ?: "Unknown Title"

                    val albumMethod = trackClass?.getMethod("getAlbum")
                    val album = albumMethod?.invoke(track)
                    val albumNameMethod = album?.javaClass?.getMethod("getName")
                    val albumName = albumNameMethod?.invoke(album) as? String ?: "Unknown Album"

                    val albumImagesMethod = album?.javaClass?.getMethod("getImages")
                    val albumImages = albumImagesMethod?.invoke(album) as? List<*>
                    val firstImage = albumImages?.firstOrNull()
                    val imageUrlMethod = firstImage?.javaClass?.getMethod("getUrl")
                    val imageUrl = imageUrlMethod?.invoke(firstImage) as? String

                    val artistsMethod = trackClass?.getMethod("getArtists")
                    val artists = artistsMethod?.invoke(track) as? List<*>
                    val firstArtist = artists?.firstOrNull()
                    val artistNameMethod = firstArtist?.javaClass?.getMethod("getName")
                    val artistName = artistNameMethod?.invoke(firstArtist) as? String ?: "Unknown Artist"

                    // If the artwork URL is null, use a default URL
                    val finalArtworkUrl = if (imageUrl.isNullOrBlank()) {
                        // Use a default artwork URL if none is available
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/Spotify_logo_without_text.svg/1024px-Spotify_logo_without_text.svg.png"
                    } else {
                        imageUrl
                    }

                    Logger.d { "Spotify track: $title, artwork URL: $finalArtworkUrl" }

                    SpotifySearchResult(
                        title = title,
                        album = albumName,
                        artist = artistName,
                        artworkUrl = finalArtworkUrl
                    )
                } catch (e: Exception) {
                    Logger.e { "Failed to extract track information: ${e.message}" }
                    null
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Logger.e { "Failed to search Spotify: ${e.message}" }
            Result.failure(e)
        }
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
