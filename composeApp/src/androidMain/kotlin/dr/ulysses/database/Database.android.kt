package dr.ulysses.database

import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DriverFactory(private val context: Context) {
    actual suspend fun createDriver(name: String): SqlDriver {
        return AndroidSqliteDriver(Database.Schema.synchronous(), context, name)
    }
}
