package dr.ulysses.entities

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dr.ulysses.database.SharedDatabase
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

enum class SettingKey {
    SongsPath,
    PlaylistPath,
    DefaultServerAddress,
    SpotifyClientId,
    SpotifyClientSecret;

    override fun toString() = name

    companion object {
        fun fromString(value: String?) = value?.let { value ->
            entries.find { it.name == value }
        } ?: SongsPath
    }
}

@Serializable
data class Setting(
    val key: SettingKey,
    val value: String?,
)

object SettingsRepository : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    private fun mapSetting(
        key: String,
        value: String?,
    ): Setting = Setting(
        key = SettingKey.fromString(key),
        value = value
    )

    suspend fun insert(setting: Setting) = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.transactionWithResult {
            appDatabase.settingQueries.insert(
                key = setting.key.toString(),
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

    suspend fun get(key: SettingKey): Setting? = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.selectFirstByKey(key.toString()).awaitAsOneOrNull()?.let {
            mapSetting(
                key = it.key,
                value = it.value_,
            )
        }
    }

    suspend fun update(setting: Setting) = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.transactionWithResult {
            appDatabase.settingQueries.updateByKey(
                key = setting.key.toString(),
                value_ = setting.value,
            )
        }
    }

    suspend fun upsert(setting: Setting) = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.transactionWithResult {
            // First check if the setting exists
            val exists = appDatabase
                .settingQueries
                .selectFirstByKey(setting.key.toString())
                .executeAsOneOrNull() != null

            if (exists) {
                // Update existing setting
                appDatabase.settingQueries.updateByKey(
                    key = setting.key.toString(),
                    value_ = setting.value,
                )
            } else {
                // Insert new setting
                appDatabase.settingQueries.insert(
                    key = setting.key.toString(),
                    value_ = setting.value,
                )
            }
        }
    }

    suspend fun delete(key: SettingKey) = sharedDatabase { appDatabase ->
        appDatabase.settingQueries.transactionWithResult {
            appDatabase.settingQueries.deleteByKey(key.toString())
        }
    }
}
