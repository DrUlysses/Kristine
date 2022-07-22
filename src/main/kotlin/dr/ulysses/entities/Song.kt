package dr.ulysses.entities

import java.time.Duration

enum class Status {
    Added,
    Saved,
    Stored
}

class Song(
    val title: String,
    val album: String,
    val artist: String,
    val duration: Duration,
    val path: String,
    val tags: String,
    val text: Text,
    val status: Status,
) {

}