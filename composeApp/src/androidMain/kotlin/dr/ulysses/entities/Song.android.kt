package dr.ulysses.entities

import android.content.ContentResolver
import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns.*
import androidx.annotation.RequiresApi
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import dr.ulysses.Logger
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.Q)
actual suspend fun refreshSongs(): List<Song> {
    val context: Context by inject(Context::class.java)
    val contentResolver: ContentResolver = context.contentResolver

    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    val projection = arrayOf(
        _ID,
        TITLE,
        DURATION,
        DATA,
        // Artist data
        ARTIST,
        ALBUM_ARTIST,
        COMPOSER,
        ALBUM,
    )

    contentResolver.query(
        /* uri = */collection,
        /* projection = */projection,
        /* selection = */
        "${MediaStore.Audio.AudioColumns.IS_MUSIC}=1 AND $TITLE != '' AND " +
                "$DURATION>= ${
                    TimeUnit.MILLISECONDS.convert(
                        10,
                        TimeUnit.SECONDS
                    )
                }",
        /* selectionArgs = */arrayOf(),
        /* sortOrder = */"$_ID ASC"
    )?.use { cursor ->
        val colTitle = cursor.getColumnIndexOrThrow(TITLE)
        val colLocation = cursor.getColumnIndexOrThrow(DATA)
        val colAlbum = cursor.getColumnIndexOrThrow(ALBUM)
        val colDuration = cursor.getColumnIndexOrThrow(DURATION)
        val colArtist = cursor.getColumnIndexOrThrow(ARTIST)
        val colAlbumArtist = cursor.getColumnIndexOrThrow(ALBUM_ARTIST)
        val colComposer = cursor.getColumnIndexOrThrow(COMPOSER)
        while (cursor.moveToNext()) {
            try {
                val path = cursor.getStringOrNull(colLocation) ?: ""
                SongRepository.upsert(
                    Song(
                        title = cursor.getStringOrNull(colTitle) ?: "Unknown",
                        path = path.also { Logger.d { "Path: $it" } },
                        // Skip loading artwork during refresh to improve performance
                        // Artwork will be loaded on-demand when needed using SongRepository.getArtwork()
                        artwork = null,
                        album = cursor.getStringOrNull(colAlbum),
                        duration = cursor.getIntOrNull(colDuration),
                        artist = with(cursor) {
                            getStringOrNull(colArtist) ?: //
                            getStringOrNull(colAlbumArtist) ?: //
                            getStringOrNull(colComposer) ?: //
                            ""
                        },
                    )
                )
            } catch (_: CursorIndexOutOfBoundsException) {
                continue
            } catch (_: SQLiteConstraintException) {
                continue
            }
        }
        cursor.close()
    }

    return SongRepository.getAllSongs().sortedBy { it.title }
}
