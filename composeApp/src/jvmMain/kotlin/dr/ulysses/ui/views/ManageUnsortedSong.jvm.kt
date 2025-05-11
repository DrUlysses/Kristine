package dr.ulysses.ui.views

import dr.ulysses.entities.Song
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import kotlin.io.path.Path

actual fun onSongSave(song: Song): Result<Song> {
    val file = Path(song.path).toFile().also {
        if (!it.exists()) {
            return Result.failure(IllegalArgumentException("Song file doesn't exists"))
        }
    }
    return runCatching {
        AudioFileIO.read(file).apply {
            tag.apply {
                setField(FieldKey.TITLE, song.title)
                setField(FieldKey.ALBUM, song.album.orEmpty())
                setField(FieldKey.ARTIST, song.artist)
                setField(FieldKey.TRACK_TOTAL, song.duration.toString())
                song.artwork?.let { firstArtwork.binaryData = it }
            }
            commit()
        }
        song
    }
}
