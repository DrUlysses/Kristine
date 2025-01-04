package dr.ulysses.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

actual class DriverFactory {
    actual suspend fun createDriver(
        name: String,
    ): SqlDriver = WebWorkerDriver(
        jsWorker()
    )
}

fun jsWorker(): Worker =
    js("""new Worker(new URL("./sqljs.worker.js", import.meta.url))""")
