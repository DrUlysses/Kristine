package dr.ulysses.network

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dr.ulysses.database.SharedDatabase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Trust on first use: the certificate fingerprint pinned for each server address. */
object ServerPins : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    suspend fun get(address: String): String? = sharedDatabase { db ->
        db.serverPinQueries.selectFingerprint(address).awaitAsOneOrNull()
    }

    suspend fun save(
        address: String,
        fingerprint: String,
    ) = sharedDatabase { db ->
        db.serverPinQueries.upsert(
            address = address,
            fingerprint = fingerprint
        )
    }

    suspend fun forget(address: String) = sharedDatabase { db ->
        db.serverPinQueries.deleteByAddress(address)
    }
}
