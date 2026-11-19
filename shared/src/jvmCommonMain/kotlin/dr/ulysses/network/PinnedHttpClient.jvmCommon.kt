package dr.ulysses.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

// shortcut: javax.net.ssl is the only way to give OkHttp a trust decision; there is no Kotlin alternative
internal actual fun pinnedHttpClient(
    pinnedFingerprint: String?,
    onCertificate: (String) -> Unit,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient {
    // Self-signed certificates have no authority to check, so the fingerprint is the whole trust decision
    val trustManager = object : X509TrustManager {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val fingerprint = chain.first().fingerprint()
            onCertificate(fingerprint)
            if (pinnedFingerprint != null && fingerprint != pinnedFingerprint) {
                throw CertificateException("The server's certificate changed to $fingerprint")
            }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            throw CertificateException("Clients aren't authenticated by certificate")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(
            null,
            arrayOf(trustManager),
            null
        )
    }
    return HttpClient(OkHttp) {
        engine {
            config {
                sslSocketFactory(
                    sslSocketFactory = sslContext.socketFactory,
                    trustManager = trustManager
                )
                // The certificate names no LAN address; the pinned fingerprint identifies the server instead
                hostnameVerifier { _, _ -> true }
            }
        }
        block()
    }
}
