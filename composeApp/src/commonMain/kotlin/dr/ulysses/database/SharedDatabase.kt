package dr.ulysses.database

import app.cash.sqldelight.async.coroutines.awaitCreate

class SharedDatabase(
    private val driverFactory: DriverFactory,
) {
    lateinit var database: Database

    private suspend fun initDatabase() {
        if (!::database.isInitialized) {
            val driver = driverFactory.createDriver("kristine.db")
            database = Database(driver).also {
                Database.Schema.awaitCreate(driver)
            }
        }
    }

    suspend operator fun <R> invoke(block: suspend (Database) -> R): R {
        initDatabase()
        return block(database)
    }
}
