package dr.ulysses.network

import io.ktor.network.tls.certificates.*
import io.ktor.network.tls.extensions.*
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate
import javax.security.auth.x500.X500Principal

/** Where this device keeps files that must survive restarts, like the server certificate. */
internal expect fun appDataDirectory(): String

/**
 * The server's self-signed certificate, created on the first start and kept, so that clients which pinned its
 * fingerprint keep trusting it.
 */
internal object ServerCertificate {
    const val ALIAS = "kristine"

    // shortcut: a fixed password, since the key store sits in the app's own data folder; derive one per device if that folder isn't private
    const val PASSWORD = "kristine"

    private val file = Path(
        appDataDirectory(),
        "server-certificate.keystore"
    )

    val keyStore: KeyStore by lazy { load() ?: create() }

    val fingerprint: String by lazy { keyStore.getCertificate(ALIAS).fingerprint() }

    private fun load(): KeyStore? = runCatching {
        if (!SystemFileSystem.exists(file)) {
            return null
        }
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            SystemFileSystem.source(file).buffered().asInputStream().use {
                load(
                    it,
                    PASSWORD.toCharArray()
                )
            }
        }
    }.getOrNull()

    private fun create(): KeyStore = buildKeyStore {
        certificate(ALIAS) {
            password = PASSWORD
            subject = X500Principal("CN=Kristine, O=Kristine")
            hash = HashAlgorithm.SHA256
            keySizeInBits = 2048
            // Clients check the pinned fingerprint, not the name or the expiry
            daysValid = 36500
        }
    }.also { store ->
        SystemFileSystem.sink(file).buffered().asOutputStream().use {
            store.store(
                it,
                PASSWORD.toCharArray()
            )
        }
    }
}

/** SHA-256 of the certificate, as colon-separated hex like browsers show it. */
internal fun Certificate.fingerprint(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded)
        .joinToString(":") {
            it.toUByte().toString(16).padStart(
                length = 2,
                padChar = '0'
            ).uppercase()
        }
