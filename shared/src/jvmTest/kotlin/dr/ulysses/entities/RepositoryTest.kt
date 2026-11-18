package dr.ulysses.entities

import dr.ulysses.database.DriverFactory
import dr.ulysses.database.SharedDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RepositoryTest {
    private companion object {
        // The repositories are objects that resolve the DB once, so all tests share one file DB
        init {
            val dir = Path(SystemTemporaryDirectory, "kristine-test-${Random.nextLong()}")
            SystemFileSystem.createDirectories(dir)
            startKoin { modules(module { single { SharedDatabase(DriverFactory(dir.toString())) } }) }
        }
    }

    private fun song(title: String) = Song(path = "file:///${Random.nextLong()}.mp3", title = title, artist = "Artist")

    @Test
    fun `a rescan keeps the user's edits`() = runBlocking {
        val scanned = song("track01")
        SongRepository.insertIfNew(scanned)
        SongRepository.upsert(scanned.copy(title = "Edited", artwork = byteArrayOf(1), state = Song.State.Sorted))

        SongRepository.insertIfNew(scanned)

        val stored = SongRepository.getByPathOrNull(scanned.path)!!
        assertEquals("Edited", stored.title)
        assertEquals(Song.State.Sorted, stored.state)
        assertContentEquals(byteArrayOf(1), stored.artwork)
    }

    @Test
    fun `a renamed playlist keeps its songs`() = runBlocking {
        val song = song("In a playlist")
        val name = "Rock ${Random.nextLong()}"
        SongRepository.insertIfNew(song)
        PlaylistRepository.insert(Playlist(name = name, songs = listOf(song, song)))

        PlaylistRepository.rename(name, "$name 2")

        assertEquals(listOf(song.path), PlaylistRepository.getPlaylistSongs("$name 2").map { it.path })
    }

    @Test
    fun `search matches part of a title`() = runBlocking {
        val song = song("Hey Jude ${Random.nextLong()}")
        SongRepository.insertIfNew(song)

        assertEquals(listOf(song.path), SongRepository.search(song.title.substring(4)).map { it.path })
    }
}
