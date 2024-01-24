package dr.ulysses.entities

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import org.koin.java.KoinJavaComponent.inject


actual suspend fun refreshSongs(): List<Song> {
    val context: Context by inject(Context::class.java)
    val contentResolver: ContentResolver = context.contentResolver
    val songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val cursor = contentResolver.query(songUri, null, null, null, null)

    if (cursor != null && cursor.moveToFirst()) {
        val songTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val songArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val songLocation = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        do {
            SongRepository().insert(
                Song(
                    title = cursor.getString(songTitle),
                    artist = cursor.getString(songArtist),
                    path = cursor.getString(songLocation),
                    status = "downloaded"
                )
            )
        } while (cursor.moveToNext())
        cursor.close()
    }

    return SongRepository().getAll()
}
