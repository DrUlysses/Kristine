package dr.ulysses.player

import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import kotlinx.coroutines.*

/** The queue item for [Song]; the media id carries the path because controllers strip the URI. */
internal fun Song.toMediaItem(mediaId: String = path): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(path.toUri())
    .setMediaMetadata(
        // The current song's artwork comes from the file's own tags, read by the player
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()

/**
 * Serves the library to Android Auto, Bluetooth and other browsers:
 * root → Songs / Artists / Albums / Playlists → songs. A song picked there plays within its folder.
 */
open class MediaLibrarySessionCallback(context: Context) :
    MediaLibraryService.MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun release() = scope.cancel()

    private val customLayoutCommandButtons: List<CommandButton> =
        listOf(
            CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName(context.getString(androidx.media3.ui.R.string.exo_controls_shuffle_on_description))
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON, Bundle.EMPTY))
                .setCustomIconResId(androidx.media3.ui.R.drawable.exo_icon_shuffle_off)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setDisplayName(context.getString(androidx.media3.ui.R.string.exo_controls_shuffle_off_description))
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF, Bundle.EMPTY))
                .setCustomIconResId(androidx.media3.ui.R.drawable.exo_icon_shuffle_on)
                .build()
        )

    @UnstableApi // MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
    val mediaNotificationSessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .also { builder ->
                customLayoutCommandButtons.forEach { commandButton ->
                    commandButton.sessionCommand?.let { builder.add(it) }
                }
            }
            .build()

    @UnstableApi
    val mediaPlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon().build()

    @UnstableApi
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        if (
            session.isMediaNotificationController(controller) ||
            session.isAutomotiveController(controller) ||
            session.isAutoCompanionController(controller)
        ) {
            // The button shows the action that toggles the current mode
            val customLayout = customLayoutCommandButtons[if (session.player.shuffleModeEnabled) {
                1
            } else {
                0
            }]
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(mediaNotificationSessionCommands)
                .setAvailablePlayerCommands(mediaPlayerCommands)
                .setCustomLayout(ImmutableList.of(customLayout))
                .build()
        }
        session.player.shuffleModeEnabled = Player.state.shuffle
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
    }

    @UnstableApi // MediaSession.isMediaNotificationController
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON == customCommand.customAction) {
            Player.setShuffle(true)
            session.player.shuffleModeEnabled = Player.state.shuffle
            session.setCustomLayout(
                session.mediaNotificationControllerInfo!!,
                ImmutableList.of(customLayoutCommandButtons[1])
            )
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        } else if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF == customCommand.customAction) {
            Player.setShuffle(false)
            session.player.shuffleModeEnabled = Player.state.shuffle
            session.setCustomLayout(
                session.mediaNotificationControllerInfo!!,
                ImmutableList.of(customLayoutCommandButtons[0])
            )
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(
            LibraryResult.ofItem(
                folder(
                    id = ROOT,
                    title = "Kristine"
                ), params
            )
        )

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        SongRepository.getByPathOrNull(mediaId.substringAfter(PLAY))
            ?.let {
                LibraryResult.ofItem(
                    it.copy(
                        artwork = null
                    ).toMediaItem(mediaId), null
                )
            }
            ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        children(parentId)?.let {
            LibraryResult.ofItemList(
                it.page(
                    page = page,
                    pageSize = pageSize
                ), params
            )
        }
            ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }

    private suspend fun children(parentId: String): List<MediaItem>? = when (parentId) {
        ROOT -> listOf(
            folder(
                id = SONGS,
                title = "Songs"
            ),
            folder(
                id = ARTISTS,
                title = "Artists"
            ),
            folder(
                id = ALBUMS,
                title = "Albums"
            ),
            folder(
                id = PLAYLISTS,
                title = "Playlists"
            )
        )

        ARTISTS -> library().map {
            it.artist
        }.distinct().sorted().map {
            folder(
                id = "$ARTIST$SEP$it",
                title = it
            )
        }

        ALBUMS -> library().mapNotNull {
            it.album
        }.distinct().sorted().map {
            folder(
                id = "$ALBUM$SEP$it",
                title = it
            )
        }

        PLAYLISTS -> PlaylistRepository.getAllPlaylists().map {
            folder(
                id = "$PLAYLIST$SEP${it.name}",
                title = it.name
            )
        }

        else -> songsOf(parentId)?.map {
            it.toMediaItem("$parentId$PLAY${it.path}")
        }
    }

    /** The playable songs of a song folder, or null if [parentId] isn't one. */
    private suspend fun songsOf(parentId: String): List<Song>? {
        val kind = parentId.substringBefore(SEP)
        val name = parentId.substringAfter(
            delimiter = SEP,
            missingDelimiterValue = ""
        )
        val songs = when (kind) {
            SONGS -> library()
            ARTIST -> library().filter { it.artist == name }
            ALBUM -> library().filter { it.album == name }
            PLAYLIST -> PlaylistRepository.getPlaylistSongs(name)
            SEARCH -> SongRepository.search(name)
            else -> return null
        }
        return songs.filter { it.state != Song.State.NotDownloaded }
    }

    private suspend fun library() = SongRepository.getAllSongs().sortedBy { it.title.lowercase() }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        return Futures.immediateFuture(resolveMediaItems(mediaItems))
    }

    @UnstableApi // MediaSession.MediaItemsWithStartPosition
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        browser: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        // A song picked while browsing, or a voice request ("play …"), starts its whole folder
        val picked = mediaItems.singleOrNull()?.let { item ->
            item.requestMetadata.searchQuery?.let { "$SEARCH$SEP$it$PLAY" } ?: item.mediaId.takeIf { PLAY in it }
        } ?: return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(resolveMediaItems(mediaItems), startIndex, startPositionMs)
        )
        return future {
            val parentId = picked.substringBefore(PLAY)
            val songs = songsOf(parentId).orEmpty()
            val index = songs.indexOfFirst { it.path == picked.substringAfter(PLAY) }.coerceAtLeast(0)
            // The app shows what the device now plays
            withContext(Dispatchers.Main) {
                Player.onQueueSetByDevice(
                    playlist = Playlist(
                        name = parentId.substringAfter(SEP),
                        songs = songs
                    ),
                    trackNum = index
                )
            }
            MediaSession.MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, index, 0)
        }
    }

    // Media ids are the path, or a browse id ending in it; the metadata the controller sent stays
    private fun resolveMediaItems(mediaItems: List<MediaItem>): List<MediaItem> = mediaItems.map {
        val path = it.mediaId.substringAfter(PLAY)
        it.buildUpon().setMediaId(path).setUri(path.toUri()).build()
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = future {
        session.notifySearchResultChanged(browser, query, songsOf("$SEARCH$SEP$query").orEmpty().size, params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        LibraryResult.ofItemList(
            children("$SEARCH$SEP$query").orEmpty().page(
                page = page,
                pageSize = pageSize
            ), params
        )
    }

    @UnstableApi
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val mediaItems = Player.state.currentPlaylist.songs.map { it.toMediaItem() }
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(mediaItems, Player.state.currentTrackNum, 0)
        )
    }

    // Callbacks run on the main thread; their database reads move off it
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> = SettableFuture.create<T>().also { result ->
        scope.launch {
            runCatching { block() }.fold(
                onSuccess = { result.set(it) },
                onFailure = { result.setException(it) }
            )
        }
    }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private fun List<MediaItem>.page(page: Int, pageSize: Int) =
        drop((page.toLong() * pageSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(pageSize)

    companion object {
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON =
            "kristine.SHUFFLE_ON"
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF =
            "kristine.SHUFFLE_OFF"

        // Browse ids: "<kind>", "<kind>␟<name>", and a song inside a folder "<folder id>␞<path>"
        private const val SEP = '\u001F'
        private const val PLAY = '\u001E'
        private const val ROOT = "root"
        private const val SONGS = "songs"
        private const val ARTISTS = "artists"
        private const val ALBUMS = "albums"
        private const val PLAYLISTS = "playlists"
        private const val ARTIST = "artist"
        private const val ALBUM = "album"
        private const val PLAYLIST = "playlist"
        private const val SEARCH = "search"
    }
}
