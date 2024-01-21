package dr.ulysses.entities

data class Song(
    val id: Int,
    val title: String,
    val album: String?,
    val artist: String,
    val path: String?,
    val duration: Int,
    val status: String
)
