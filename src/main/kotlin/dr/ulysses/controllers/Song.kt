package dr.ulysses.controllers

import dr.ulysses.entities.DbTag
import dr.ulysses.entities.DtoSong
import dr.ulysses.misc.toDtoSong
import org.jetbrains.exposed.sql.SizedCollection
import dr.ulysses.entities.Song as SongEntity
import dr.ulysses.entities.Tag as TagEntity
import dr.ulysses.entities.Status as StatusEnum
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.io.path.*

object Song {
    fun add(file: File): String {
        val song: DtoSong = file.toDtoSong() ?: return "No content"

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

    fun refreshSongs(): List<String> {
//        val path = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"

//        return transaction {
//            SongEntity.all().map { it.toDto() }
//        }
        return Path("./music").listDirectoryEntries().map { it.name }
    }
}