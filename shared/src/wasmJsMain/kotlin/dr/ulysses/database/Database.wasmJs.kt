package dr.ulysses.database

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

actual class DriverFactory {
    // sql.js keeps the DB in memory, so the schema is created on every page load
    actual suspend fun createDriver(name: String): SqlDriver = WebWorkerDriver(jsWorker()).also { driver ->
        driver.execute(null, "PRAGMA foreign_keys = ON", 0).await()
        Database.Schema.awaitCreate(driver)
    }
}

fun jsWorker(): Worker =
    js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")
