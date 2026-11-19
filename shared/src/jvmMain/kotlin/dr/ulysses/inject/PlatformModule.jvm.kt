package dr.ulysses.inject

import dr.ulysses.database.DriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Paths

actual fun platformModule(): Module = module {
    single { DriverFactory(Paths.get("").toAbsolutePath().parent.toString()) }
}
