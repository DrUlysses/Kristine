package dr.ulysses.api

import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class SpotifySearchResult(
    val title: String,
    val album: String,
    val artist: String,
    val artworkUrl: String? = null,
)

/** Track search used to fill in metadata of unsorted songs. Credentials come from the settings. */
object SpotifyAppApi {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var token: CachedToken? = null

    private class CachedToken(val credentials: String, val value: String, val expiresAt: Instant)

    suspend fun searchTrack(query: String): Result<List<SpotifySearchResult>> = runCatching {
        val response = client.get("https://api.spotify.com/v1/search") {
            parameter("q", query)
            parameter("type", "track")
            parameter("limit", 5)
            bearerAuth(accessToken())
        }
        check(response.status.isSuccess()) { "Spotify search failed: ${response.status}" }
        json.decodeFromString<SearchResponse>(response.bodyAsText()).tracks.items.map { track ->
            SpotifySearchResult(
                title = track.name,
                album = track.album.name,
                artist = track.artists.firstOrNull()?.name ?: "Unknown Artist",
                artworkUrl = track.album.images.firstOrNull()?.url,
            )
        }
    }

    // Client credentials flow; the token is reused until shortly before it expires or the credentials change
    private suspend fun accessToken(): String {
        val clientId = SettingsRepository.get(SettingKey.SpotifyClientId)?.value
        val clientSecret = SettingsRepository.get(SettingKey.SpotifyClientSecret)?.value
        require(!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) { "Spotify client ID or secret not set" }
        val credentials = "$clientId:$clientSecret"
        token?.takeIf { it.credentials == credentials && Clock.System.now() < it.expiresAt }?.let { return it.value }

        val response = client.submitForm(
            url = "https://accounts.spotify.com/api/token",
            formParameters = parameters { append("grant_type", "client_credentials") },
        ) { basicAuth(clientId, clientSecret) }
        check(response.status.isSuccess()) { "Spotify login failed: ${response.status}" }
        val body = json.decodeFromString<TokenResponse>(response.bodyAsText())
        token = CachedToken(credentials, body.accessToken, Clock.System.now() + (body.expiresIn - 60).seconds)
        return body.accessToken
    }

    @Serializable
    private class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int,
    )

    @Serializable
    private class SearchResponse(val tracks: Tracks)

    @Serializable
    private class Tracks(val items: List<Track>)

    @Serializable
    private class Track(val name: String, val album: Album, val artists: List<Artist>)

    @Serializable
    private class Album(val name: String, val images: List<Image> = emptyList())

    @Serializable
    private class Artist(val name: String)

    @Serializable
    private class Image(val url: String)
}
