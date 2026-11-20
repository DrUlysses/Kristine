package dr.ulysses.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal val http = HttpClient()
internal val json = Json { ignoreUnknownKeys = true }

/**
 * Asks [url] for JSON within the service's rate limit: past it, Deezer answers "Quota limit exceeded" and iTunes 429,
 * which turned songs into "no match" at random during Auto-sort.
 */
internal suspend inline fun <reified T> getJson(
    url: String,
    crossinline block: HttpRequestBuilder.() -> Unit = {},
): T {
    when {
        url.startsWith("https://api.deezer.com/") -> DEEZER_LIMIT.acquire()
        // shortcut: iTunes allows about 20 a minute, too few to wait for in Auto-sort; Deezer and AcoustID cover it
        url.startsWith("https://itunes.apple.com/") -> check(ITUNES_LIMIT.tryAcquire()) { "$url: over its limit" }
    }
    val response = http.get(url) { block() }
    check(response.status.isSuccess()) { "$url: ${response.status}" }
    val body = response.bodyAsText()
    // Deezer answers its errors with 200
    check(!body.startsWith("{\"error\"")) { "$url: $body" }
    return json.decodeFromString(body)
}

/**
 * At most [count] calls in any [window].
 */
internal class RateLimit(
    private val count: Int,
    private val window: Duration,
) {
    private val mutex = Mutex()
    private val calls = ArrayDeque<TimeMark>()

    /**
     * Waits for a free slot.
     */
    suspend fun acquire() {
        while (!tryAcquire()) {
            delay(window / count)
        }
    }

    /**
     * Takes a slot if one is free.
     */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        while (calls.firstOrNull()?.let { it.elapsedNow() >= window } == true) {
            calls.removeFirst()
        }
        (calls.size < count).also { isFree ->
            if (isFree) {
                calls.addLast(TimeSource.Monotonic.markNow())
            }
        }
    }
}

// Deezer allows 50 in 5 s; a margin for the clock and other Deezer use of this device
internal val DEEZER_LIMIT = RateLimit(
    count = 40,
    window = 5.seconds
)
internal val ITUNES_LIMIT = RateLimit(
    count = 20,
    window = 60.seconds
)
internal val ACOUSTID_LIMIT = RateLimit(
    count = 3,
    window = 1.seconds
)
