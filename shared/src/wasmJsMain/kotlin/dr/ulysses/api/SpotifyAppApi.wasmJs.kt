package dr.ulysses.api

// shortcut: the Spotify client library is JVM-only, so the web app finds nothing; add a Ktor-based client if web needs it
actual object SpotifyAppApi {
    actual suspend fun searchTrack(query: String): Result<List<SpotifySearchResult>> = Result.success(emptyList())
}
