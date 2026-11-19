package dr.ulysses.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SharedDatabase(
    private val driverFactory: DriverFactory,
) {
    private val mutex = Mutex()
    private var database: Database? = null

    suspend operator fun <R> invoke(block: suspend (Database) -> R): R {
        val db = mutex.withLock {
            database ?: Database(driverFactory.createDriver("kristine.db")).also { database = it }
        }
        return block(db)
    }
}
