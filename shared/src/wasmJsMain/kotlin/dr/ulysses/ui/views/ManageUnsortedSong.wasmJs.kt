package dr.ulysses.ui.views

import dr.ulysses.entities.Song

// The browser can't write into audio files
actual fun onSongSave(song: Song): Result<Song> =
    Result.failure(UnsupportedOperationException("Editing songs isn't supported on web"))
