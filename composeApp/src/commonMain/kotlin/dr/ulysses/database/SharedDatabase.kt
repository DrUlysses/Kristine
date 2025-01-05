package dr.ulysses.database

import app.cash.sqldelight.async.coroutines.awaitCreate

class SharedDatabase(
    private val driverFactory: DriverFactory,
) {
    private var database: Database? = null

    private suspend fun initDatabase() {
        if (database == null) {
            val driver = driverFactory.createDriver("kristine.db")
            database = Database(driver).also {
                Database.Schema.awaitCreate(driver)
            }
        }
    }

    suspend operator fun <R> invoke(block: suspend (Database) -> R): R {
        initDatabase()
        return block(database!!)
    }
}
