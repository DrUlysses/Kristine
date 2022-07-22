package dr.ulysses.entities

import org.jetbrains.exposed.dao.id.UUIDTable
import java.time.Duration

enum class Status {
    Added,
    Saved,
    Stored
}

class Tag (
    val name: String
)

class Song (
    val title: String,
    val album: String,
    val artist: String,
    val duration: Duration,
    val path: String,
    val tags: Tag,
    val text: Text,
    val status: Status,
) {
}

object DbSong : UUIDTable(name = "song", columnName = "id") {
    val title = varchar("title", 255)
    val album = varchar("album", 255)
    val artist = varchar("artist", 255)
    val duration = integer("duration")
    val path = varchar("path", 255)
    val tags = reference("tag", DbTag)
    val text = reference("text", DbText)
    val status = enumeration("status", Status::class)
}

object DbTag : UUIDTable(name = "tag", columnName = "id") {
    val name = varchar("name", 255)
}
