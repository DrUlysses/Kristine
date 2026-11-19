package dr.ulysses.entities

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns.*
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import org.koin.java.KoinJavaComponent.inject

actual suspend fun refreshSongs(): List<Song> {
    val context: Context by inject(Context::class.java)
    val projection = arrayOf(_ID, TITLE, DURATION, DATA, ARTIST, ALBUM_ARTIST, COMPOSER, ALBUM)
    val selection = "${MediaStore.Audio.AudioColumns.IS_MUSIC}=1 AND $TITLE != '' AND $DURATION >= 10000"

    val scanned = context.contentResolver.query(
        MediaStore.Audio.Media.getContentUri("external"), projection, selection, null, "$_ID ASC"
    )?.use { cursor ->
        val colTitle = cursor.getColumnIndexOrThrow(TITLE)
        val colLocation = cursor.getColumnIndexOrThrow(DATA)
        val colAlbum = cursor.getColumnIndexOrThrow(ALBUM)
        val colDuration = cursor.getColumnIndexOrThrow(DURATION)
        val colArtist = cursor.getColumnIndexOrThrow(ARTIST)
        val colAlbumArtist = cursor.getColumnIndexOrThrow(ALBUM_ARTIST)
        val colComposer = cursor.getColumnIndexOrThrow(COMPOSER)
        buildList<Song> {
            while (cursor.moveToNext()) {
                // Artwork is loaded on demand via SongRepository.getArtwork(), not during the scan
                add(
                    Song(
                        path = cursor.getStringOrNull(colLocation) ?: continue,
                        title = cursor.getStringOrNull(colTitle) ?: "Unknown",
                        album = cursor.getStringOrNull(colAlbum),
                        duration = cursor.getIntOrNull(colDuration)?.div(1000), // MediaStore gives milliseconds
                        artist = cursor.getStringOrNull(colArtist)
                            ?: cursor.getStringOrNull(colAlbumArtist)
                            ?: cursor.getStringOrNull(colComposer)
                            ?: "",
                    )
                )
            }
        }
    } ?: return SongRepository.getAllSongs().sortedBy { it.title } // No storage access: nothing to compare against

    SongRepository.syncWithScan(scanned)
    return SongRepository.getAllSongs().sortedBy { it.title }
}

actual fun getDurationFromMetadata(path: String): Int? = runCatching {
    MediaMetadataRetriever().run {
        try {
            setDataSource(path.substringAfter("file:///"))
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()?.div(1000)?.toInt()
        } finally {
            release()
        }
    }
}.getOrNull()
