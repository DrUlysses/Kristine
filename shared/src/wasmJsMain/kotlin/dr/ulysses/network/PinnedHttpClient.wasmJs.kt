package dr.ulysses.network

import io.ktor.client.*
import io.ktor.client.engine.js.*

// The browser decides about certificates itself: the server's https page has to be opened and accepted once
internal actual fun pinnedHttpClient(
    pinnedFingerprint: String?,
    onCertificate: (String) -> Unit,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(Js) {
    block()
}
