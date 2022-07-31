package dr.ulysses.entities

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

class Tag (id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<Tag>(DbTag)

    var name  by DbTag.name
    var songs by Song via DbSongTags
}

object DbTag : UUIDTable(name = "tag", columnName = "id") {
    val name = text("name")
}