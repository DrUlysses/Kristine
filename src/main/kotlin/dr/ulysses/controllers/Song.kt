package dr.ulysses.controllers

import dr.ulysses.entities.DbTag
import org.jetbrains.exposed.sql.SizedCollection
import dr.ulysses.entities.Song as SongEntity
import dr.ulysses.entities.Tag as TagEntity
import dr.ulysses.entities.Status as StatusEnum
import org.jetbrains.exposed.sql.transactions.transaction

class DtoSong(
    var title: String,
    var album: String,
    var artist: String,
    var duration: Int,
    var path: String,
    var tags: List<String>,
    var text: List<String>?,
    var status: String,
)

object Song {
    fun add(song: DtoSong): String {
        val oldTags = TagEntity.find { DbTag.name.inList(song.tags) }
        val existing = oldTags.map { it.name }
        val newTags = song.tags.filter { it !in existing }.map {
            transaction {
                TagEntity.new {
                    name = it
                }
            }
        }

        transaction {
            SongEntity.new {
                title = song.title
                album = song.album
                artist = song.artist
                duration = song.duration
                path = song.path
                tags = SizedCollection(oldTags + newTags)
                text = null
                status = StatusEnum.valueOf(song.status)
            }
        }
        return "Success"
    }
}