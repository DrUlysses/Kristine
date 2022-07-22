package dr.ulysses.entities

import org.jetbrains.exposed.dao.id.UUIDTable

class Text(
    val next: Int,
    val first: Int,
    val start: Int,
    val end: Int,
    val line: String
) {
}

object DbText : UUIDTable(name = "text", columnName = "id") {
    val next = uuid("next").references(DbText.id)
    val first = uuid("first").references(DbText.id)
    val start = integer("start")
    val end = integer("end")
    val line = varchar("line", 255)
}