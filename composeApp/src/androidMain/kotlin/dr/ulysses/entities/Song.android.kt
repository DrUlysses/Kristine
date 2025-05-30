package dr.ulysses.entities

import android.content.ContentResolver
import android.content.Context
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteConstraintException
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns.*
import androidx.annotation.RequiresApi
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import dr.ulysses.Logger
import dr.ulysses.network.NetworkManager.currentServer
import dr.ulysses.network.NetworkManager.fetchSongsFromCurrentServer
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists

@RequiresApi(Build.VERSION_CODES.Q)
actual suspend fun refreshSongs(): List<Song> {
    // Check if connected to a server
    if (currentServer.value != null) {
        // Try to fetch songs from the server
        val serverSongs = fetchSongsFromCurrentServer()
        if (serverSongs != null) {
            // If server songs were successfully fetched, return them
            Logger.d { "Using songs from server: ${serverSongs.size} songs" }
            return serverSongs
        }
        // If server songs couldn't be fetched, fall back to local songs
        Logger.d { "Failed to fetch songs from server, falling back to local songs" }
    }

    // Not connected to a server or failed to fetch server songs, load local songs
    Logger.d { "Loading songs from local storage" }
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

    val localSongs = SongRepository.getAllSongs().sortedBy { it.title }
    Logger.d { "Loaded ${localSongs.size} songs from local storage" }
    return localSongs
}

actual fun fileExists(path: String): Boolean = Path(path.substringAfter("file:///")).exists()

/**
 * Gets the duration in seconds from the metadata of a file.
 * @param path The path to the file
 * @return The duration in seconds, or null if it couldn't be determined
 */
actual fun getDurationFromMetadata(path: String): Int? {
    return try {
        val retriever = MediaMetadataRetriever()
        val filePath = path.substringAfter("file:///")
        retriever.setDataSource(filePath)

        // Get duration in milliseconds and convert to seconds
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        val durationSec = (durationMs / 1000).toInt()

        retriever.release()
        durationSec
    } catch (e: Exception) {
        Logger.e { "Error reading duration metadata for $path: ${e.message}" }
        null
    }
}
