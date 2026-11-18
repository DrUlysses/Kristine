package dr.ulysses.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.io.files.Path

actual class DriverFactory(private val appPath: String) {
    // Passing the schema makes the driver create or migrate the DB by its user_version;
    // sqlite-jdbc applies pragmas given in the URL to every connection
    actual suspend fun createDriver(name: String): SqlDriver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${Path(appPath, name)}?foreign_keys=true",
        schema = Database.Schema.synchronous(),
    )
}
