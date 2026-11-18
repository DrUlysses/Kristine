package dr.ulysses.api

import com.adamratzman.spotify.spotifyAppApi
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.adamratzman.spotify.SpotifyAppApi as SpotifyApi

actual object SpotifyAppApi {
    private var api: SpotifyApi? = null
    private var apiCredentials: Pair<String, String>? = null

    actual suspend fun searchTrack(query: String): Result<List<SpotifySearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            api().search.searchTrack(query, limit = 5).items.map { track ->
                SpotifySearchResult(
                    title = track.name,
                    album = track.album.name,
                    artist = track.artists.firstOrNull()?.name ?: "Unknown Artist",
                    artworkUrl = track.album.images?.firstOrNull()?.url,
                )
            }
        }
    }

    // Rebuilt when the user changes the credentials in the settings
    private suspend fun api(): SpotifyApi {
        val clientId = SettingsRepository.get(SettingKey.SpotifyClientId)?.value
        val clientSecret = SettingsRepository.get(SettingKey.SpotifyClientSecret)?.value
        require(!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) { "Spotify client ID or secret not set" }
        val credentials = clientId to clientSecret
        return api?.takeIf { apiCredentials == credentials }
            ?: spotifyAppApi(clientId, clientSecret).build().also {
                api = it
                apiCredentials = credentials
            }
    }
}
