package dr.ulysses.entities

import android.content.ContentResolver
import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
import android.provider.MediaStore.MediaColumns.*
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import dr.ulysses.Logger
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit


actual suspend fun refreshSongs(): List<Song> {
    val context: Context by inject(Context::class.java)
    val contentResolver: ContentResolver = context.contentResolver

    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            EXTERNAL_CONTENT_URI

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
        "${MediaStore.Audio.AudioColumns.IS_MUSIC}=1 AND ${MediaStore.Audio.AudioColumns.TITLE} != '' AND " +
                "${MediaStore.Audio.Media.DURATION}>= ${
                    TimeUnit.MILLISECONDS.convert(
                        10,
                        TimeUnit.SECONDS
                    )
                }",
        /* selectionArgs = */arrayOf(),
        /* sortOrder = */"${MediaStore.Audio.Media._ID} ASC"
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
                        artwork = try {
//                            ByteArrayOutputStream().use { stream ->
//                                ThumbnailUtils.createAudioThumbnail(
//                                    File(path),
//                                    Size(/* width = */
//                                        context.resources.getDimensionPixelSize(R.dimen.thumbnail_width),
//                                        /* height = */
//                                        context.resources.getDimensionPixelSize(R.dimen.thumbnail_height)
//                                    ),
//                                    null
//                                ).compress(Bitmap.CompressFormat.PNG, 100, stream)
//                                stream.toByteArray()
//                            }
                            null // TODO: Slows down the app
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        },
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
