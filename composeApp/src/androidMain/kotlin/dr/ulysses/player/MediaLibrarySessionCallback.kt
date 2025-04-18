package dr.ulysses.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
open class MediaLibrarySessionCallback(context: Context) :
    MediaLibraryService.MediaLibrarySession.Callback {

    private val customLayoutCommandButtons: List<CommandButton> =
        listOf(
            CommandButton.Builder()
                .setDisplayName(context.getString(androidx.media3.ui.R.string.exo_controls_shuffle_on_description))
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON, Bundle.EMPTY))
                .setIconResId(androidx.media3.ui.R.drawable.exo_icon_shuffle_off)
                .build(),
            CommandButton.Builder()
                .setDisplayName(context.getString(androidx.media3.ui.R.string.exo_controls_shuffle_off_description))
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF, Bundle.EMPTY))
                .setIconResId(androidx.media3.ui.R.drawable.exo_icon_shuffle_on)
                .build()
        )

    @UnstableApi // MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
    val mediaNotificationSessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .also { builder ->
                // Put all custom session commands in the list that may be used by the notification.
                customLayoutCommandButtons.forEach { commandButton ->
                    commandButton.sessionCommand?.let { builder.add(it) }
                }
            }
            .build()

    @UnstableApi
    val mediaPlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon().build()


    // ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
    // ConnectionResult.AcceptedResultBuilder
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
            // Select the button to display.
            val customLayout = customLayoutCommandButtons[if (session.player.shuffleModeEnabled) 1 else 0]
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(mediaNotificationSessionCommands)
                .setAvailablePlayerCommands(mediaPlayerCommands)
                .setCustomLayout(ImmutableList.of(customLayout))
                .build()
        }
        session.player.shuffleModeEnabled = Player.state.shuffle
        // Default commands without custom layout for common controllers.
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
    }

    @UnstableApi // MediaSession.isMediaNotificationController
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON == customCommand.customAction) {
            // Enable shuffling.
            Player.setShuffle(true)
            session.player.shuffleModeEnabled = Player.state.shuffle
            // Change the custom layout to contain the `Disable shuffling` command.
            session.setCustomLayout(
                session.mediaNotificationControllerInfo!!,
                ImmutableList.of(customLayoutCommandButtons[1])
            )
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        } else if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF == customCommand.customAction) {
            Player.setShuffle(false)
            session.player.shuffleModeEnabled = Player.state.shuffle
            // Change the custom layout to contain the `Enable shuffling` command.
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
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(
            LibraryResult.ofItem(
                MediaItem.fromUri(
                    Player.state.currentSong?.path ?: ""
                ), params
            )
        )
    }

    @UnstableApi
    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Player.state.currentPlaylist.songs.find { it.path == mediaId }?.let {
            return Futures.immediateFuture(LibraryResult.ofItem(MediaItem.fromUri(it.path), /* params= */ null))
        } ?: Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))

    @UnstableApi
    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
//        val children = MediaItemTree.getChildren(parentId)
//        if (children.isNotEmpty()) {
//            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
//        }
        return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
    }

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
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(resolveMediaItems(mediaItems), startIndex, startPositionMs)
        )
    }

    // TODO: bug here
    private fun resolveMediaItems(mediaItems: List<MediaItem>): List<MediaItem> =
        Player.state.currentPlaylist.songs.map { MediaItem.fromUri(it.path) }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, Player.onFindSongCommand(query).size, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return Futures.immediateFuture(
            LibraryResult.ofItemList(
                Player.onFindSongCommand(query).map { MediaItem.fromUri(it.path) }, params
            )
        )
    }

    @UnstableApi
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        // Get the current playlist and track
        val currentTrackNum = Player.state.currentTrackNum
        val mediaItems = Player.state.currentPlaylist.songs.map { MediaItem.fromUri(it.path) }

        // Return the current media items with the current position
        // Let the framework handle the actual playback resumption
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(mediaItems, currentTrackNum, 0)
        )
    }

    companion object {
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON =
            "kristine.SHUFFLE_ON"
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF =
            "kristine.SHUFFLE_OFF"
    }
}
