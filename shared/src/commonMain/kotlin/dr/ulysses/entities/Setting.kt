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
    SpotifyClientSecret,
}

@Serializable
data class Setting(
    val key: SettingKey,
    val value: String?,
)

object SettingsRepository : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    // Rows with keys this version doesn't know are skipped
    suspend fun getAll(): List<Setting> = sharedDatabase { db ->
        db.settingQueries.selectAll().awaitAsList().mapNotNull { row ->
            SettingKey.entries.find { it.name == row.key }?.let { Setting(it, row.value_) }
        }
    }

    suspend fun get(key: SettingKey): Setting? = sharedDatabase { db ->
        db.settingQueries.selectFirstByKey(key.name).awaitAsOneOrNull()?.let { Setting(key, it.value_) }
    }

    suspend fun upsert(setting: Setting) = sharedDatabase { db ->
        db.settingQueries.upsert(key = setting.key.name, value_ = setting.value)
    }
}
