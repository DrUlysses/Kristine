package dr.ulysses.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.createDefaultWebWorkerDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DriverFactory {
    actual suspend fun createDriver(name: String): SqlDriver = createDefaultWebWorkerDriver()
}
