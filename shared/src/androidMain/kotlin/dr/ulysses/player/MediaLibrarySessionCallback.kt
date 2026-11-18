package dr.ulysses.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

open class MediaLibrarySessionCallback(context: Context) :
    MediaLibraryService.MediaLibrarySession.Callback {

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
            val customLayout = customLayoutCommandButtons[if (session.player.shuffleModeEnabled) 1 else 0]
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
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(
            LibraryResult.ofItem(
                mediaItem(Player.state.currentSong?.path ?: ""), params
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
            return Futures.immediateFuture(LibraryResult.ofItem(mediaItem(it.path), /* params= */ null))
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

    // Our items and outside controllers' items (Android Auto, Bluetooth, lock screen) carry the path as media id
    private fun resolveMediaItems(mediaItems: List<MediaItem>): List<MediaItem> =
        mediaItems.map { mediaItem(it.mediaId) }

    private fun mediaItem(path: String) = MediaItem.Builder().setMediaId(path).setUri(path).build()

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
                Player.onFindSongCommand(query).map { mediaItem(it.path) }, params
            )
        )
    }

    @UnstableApi
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val mediaItems = Player.state.currentPlaylist.songs.map { mediaItem(it.path) }
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(mediaItems, Player.state.currentTrackNum, 0)
        )
    }

    companion object {
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON =
            "kristine.SHUFFLE_ON"
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF =
            "kristine.SHUFFLE_OFF"
    }
}
