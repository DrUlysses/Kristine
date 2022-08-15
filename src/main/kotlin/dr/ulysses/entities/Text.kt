package dr.ulysses.entities

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

class Text(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<Text>(DbTag)
    val song  by Text referencedOn DbText.song
    val next  by Text optionalReferencedOn DbText.next
    val first by Text referencedOn DbText.first
    val start by DbText.start
    val end   by DbText.end
    val line  by DbText.line

    fun toDtoText(): List<DtoText> {
        val res = mutableListOf<DtoText>()
        var current = this
        var next = this.next
        while (next != null) {
            res.add(
                DtoText(
                    next = next.line,
                    start = current.start.toString(),
                    end = current.end.toString(),
                    first = this.line,
                    line = current.line
                )
            )
            current = next
            next = next.next
        }
        return res
    }
}

object DbText : UUIDTable(name = "text", columnName = "id") {
    val song  = reference("song", DbSong)
    val next  = reference("next", DbText).nullable()
    val first = reference("first", DbText)
    val start = integer("start")
    val end   = integer("end")
    val line  = text("line")
}

class DtoText(
    val next: String,
    val first: String,
    val start: String,
    val end: String,
    val line: String,
)