package dr.ulysses.ui.views

import dr.ulysses.entities.Song
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.valuepair.ImageFormats
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import java.io.File

/** Writes the edited metadata into the audio file's tags. */
actual fun onSongSave(song: Song): Result<Song> = runCatching {
    val file = File(song.path.substringAfter("file:///"))
    require(file.exists()) { "Song file $file doesn't exist" }
    AudioFileIO.read(file).apply {
        tagOrCreateAndSetDefault.apply {
            setField(FieldKey.TITLE, song.title)
            setField(FieldKey.ALBUM, song.album.orEmpty())
            setField(FieldKey.ARTIST, song.artist)
            song.artwork?.let { artwork ->
                deleteArtworkField()
                // Without its type and role, MP3 players don't show the picture
                setField(
                    ArtworkFactory.getNew().apply {
                        binaryData = artwork
                        mimeType = ImageFormats.getMimeTypeForBinarySignature(artwork)
                        pictureType = PictureTypes.DEFAULT_ID
                    }
                )
            }
        }
        commit()
    }
    song
}
