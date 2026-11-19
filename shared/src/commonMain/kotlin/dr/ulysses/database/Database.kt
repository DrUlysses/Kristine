package dr.ulysses.database

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    suspend fun createDriver(name: String): SqlDriver
}
