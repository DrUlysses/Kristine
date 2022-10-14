package dr.ulysses.entities

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.and
import java.util.UUID

enum class Status {
    Added,
    Saved,
    Stored
}

class Song(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<Song>(DbSong)

    var title    by DbSong.title
    var album    by DbSong.album
    var artist   by DbSong.artist
    var duration by DbSong.duration
    var path     by DbSong.path
    var tags     by Tag via DbSongTags
    var text     by Text optionalReferencedOn DbSong.text
    var status   by DbSong.status

    fun toDtoTagsEdit() : DtoSong =
        DtoSong(
            title = this.title,
            album = this.album,
            artist = this.artist,
            duration = this.duration,
            path = this.path,
            tags = this.tags.map { it.toString() },
            text = null,
            status = this.status.toString(),
        )

    fun toDto() : DtoSong =
        DtoSong(
            title = this.title,
            album = this.album,
            artist = this.artist,
            duration = this.duration,
            path = this.path,
            tags = this.tags.map { it.toString() },
            text = this.text?.toDtoText(),
            status = this.status.toString(),
        )

    fun fromDto(dtoSong: DtoSong) : Song =
        Song.find {
            DbSong.title eq dtoSong.title and (DbSong.artist eq dtoSong.artist)
        }.firstOrNull()
            ?:
        Song.new {
            title = dtoSong.title
            album = dtoSong.album
            artist = dtoSong.artist
            duration = dtoSong.duration
            path = dtoSong.path
            tags = SizedCollection()
            text = null
            status = Status.valueOf(dtoSong.status)
        }.apply {
            dtoSong.text?.let {

            }
            if (dtoSong.tags.isNotEmpty()) {
                TODO()
            }
        }
}

object DbSong : UUIDTable(name = "song", columnName = "id") {
    val title    = text("title")
    val album    = text("album")
    val artist   = text("artist")
    val duration = integer("duration")
    val path     = text("path")
    val text     = reference("text", DbText).nullable()
    val status   = enumeration("status", Status::class)
}

object DbSongTags : UUIDTable(name = "song_tags", columnName = "id") {
    val song = reference("song", DbSong)
    val tag = reference("tag", DbTag)
}

class DtoSong(
    var title: String,
    var album: String,
    var artist: String,
    var duration: Int,
    var path: String,
    var tags: List<String>,
    var text: List<DtoText>?,
    var status: String,
)