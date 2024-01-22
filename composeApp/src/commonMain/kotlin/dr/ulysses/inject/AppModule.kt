package dr.ulysses.inject

import dr.ulysses.repositories.SongRepository
import dr.ulysses.repositories.SongRepositoryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun appModule() = module {
    singleOf(::SongRepositoryImpl) { bind<SongRepository>() }
}
