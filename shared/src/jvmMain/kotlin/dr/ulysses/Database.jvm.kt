package dr.ulysses

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.FileSystems

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DriverFactory(private val appPath: String) {
    actual fun createDriver(name: String): SqlDriver {
        val dbPath = appPath + FileSystems.getDefault().separator + name
        val driver = JdbcSqliteDriver(url = "jdbc:sqlite:$dbPath")
        Database.Schema.create(driver)
        return driver
    }
}
