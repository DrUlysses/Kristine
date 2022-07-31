package dr.ulysses.entities

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

class Text(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<Tag>(DbTag)
    val song  by Text referencedOn DbText.song
    val next  by Text referencedOn DbText.next
    val first by Text referencedOn DbText.first
    val start by DbText.start
    val end   by DbText.end
    val line  by DbText.line
}

object DbText : UUIDTable(name = "text", columnName = "id") {
    val song  = reference("song", DbSong)
    val next  = reference("next", DbText)
    val first = reference("first", DbText)
    val start = integer("start")
    val end   = integer("end")
    val line  = text("line")
}