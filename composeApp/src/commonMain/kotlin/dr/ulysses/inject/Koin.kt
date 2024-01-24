package dr.ulysses.inject

import dr.ulysses.database.SharedDatabase
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(
            sqlDelightModule,
            platformModule(),
        )
    }

val sqlDelightModule = module {
    single { SharedDatabase(get()) }
}

fun initKoin() = initKoin {}
