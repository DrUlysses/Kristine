package dr.ulysses.network

import io.ktor.client.*

/**
 * A client for one server's TLS connections. It accepts only the certificate with [pinnedFingerprint], or any
 * certificate while nothing is pinned yet; [onCertificate] gets the fingerprint the server presented.
 */
internal expect fun pinnedHttpClient(
    pinnedFingerprint: String?,
    onCertificate: (String) -> Unit,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient
