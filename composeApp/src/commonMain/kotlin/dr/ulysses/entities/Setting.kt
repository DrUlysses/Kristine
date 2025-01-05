package dr.ulysses.entities

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dr.ulysses.database.SharedDatabase
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class Setting(
    val key: String,
    val value: String?,
)

object SettingsRepository : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    private fun mapSetting(
        key: String,
        value: String?,
    ): Setting = Setting(
        key = key,
        value = value
    )

    suspend fun insert(setting: Setting) = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.transactionWithResult {
            appDatabase.settingQueries.insert(
                key = setting.key,
                value_ = setting.value,
            )
        }
    }

    suspend fun getAll(): List<Setting> = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.selectAll().awaitAsList().map {
            mapSetting(
                key = it.key,
                value = it.value_,
            )
        }
    }

    suspend fun get(key: String): Setting? = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.selectFirstByKey(key).awaitAsOneOrNull()?.let {
            mapSetting(
                key = it.key,
                value = it.value_,
            )
        }
    }

    suspend fun update(setting: Setting) = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.transactionWithResult {
            appDatabase.settingQueries.updateByKey(
                key = setting.key,
                value_ = setting.value,
            )
        }
    }

    suspend fun delete(key: String) = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.transactionWithResult {
            appDatabase.settingQueries.deleteByKey(key)
        }
    }
}
