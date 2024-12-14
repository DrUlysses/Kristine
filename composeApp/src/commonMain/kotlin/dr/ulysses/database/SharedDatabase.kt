package dr.ulysses.database

class SharedDatabase(
    private val driverFactory: DriverFactory,
) {
    private var database: Database? = null

    private suspend fun initDatabase() {
        if (database == null) {
            database = Database.invoke(
                driverFactory.createDriver("kristine.db"),
            )
        }
    }

    suspend operator fun <R> invoke(block: suspend (Database) -> R): R {
        initDatabase()
        return block(database!!)
    }
}
