package dr.ulysses.controllers

import dr.ulysses.entities.DbSong
import dr.ulysses.entities.DtoSong
import io.ktor.server.plugins.*
import dr.ulysses.entities.Song as SongEntity

object Tag {
    fun getTags(songName: String): DtoSong {
        val song: SongEntity? = SongEntity.find { (DbSong.album + " - " + DbSong.artist) eq songName }.firstOrNull()
        song?.let {
            return it.toDtoTagsEdit()
        } ?: run {
            throw NotFoundException("Song not found")
        }
    }
}