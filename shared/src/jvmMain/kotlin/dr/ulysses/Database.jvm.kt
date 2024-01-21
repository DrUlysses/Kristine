package dr.ulysses

import app.cash.sqldelight.db.QueryResult.Value
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.io.path.absolutePathString

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DriverFactory(private val directories: Directories) {
    actual fun createDriver(schema: SqlSchema<Value<Unit>>, name: String): SqlDriver {
        val dbPath = directories.userConfig.resolve("$name.db")
        val driver = JdbcSqliteDriver(url = "jdbc:sqlite:${dbPath.toNioPath().absolutePathString()}")
        schema.create(driver)
        return driver
    }
}
