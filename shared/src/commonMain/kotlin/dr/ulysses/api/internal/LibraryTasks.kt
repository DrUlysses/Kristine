package dr.ulysses.api.internal

import dr.ulysses.api.external.CatalogAlbum
import dr.ulysses.entities.*
import dr.ulysses.models.MainViewModel
import dr.ulysses.network.NetworkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface TaskCommand

@Serializable
internal data object StartAutoSortCommand : TaskCommand

/**
 * Sorts the server's own proposals for these songs, which still have their artwork.
 */
@Serializable
internal data class SortProposalsCommand(val paths: List<String>) : TaskCommand

@Serializable
internal data object FindDuplicatesCommand : TaskCommand

@Serializable
internal data class MergeNamesCommand(
    val ids: List<String>,
    val names: Map<String, String>,
) : TaskCommand

@Serializable
internal data class MergeIntoCommand(
    val songs: List<Song>,
    val album: String,
) : TaskCommand

@Serializable
internal data class DownloadCommand(val song: Song) : TaskCommand

@Serializable
internal data class DownloadFromLinkCommand(
    val link: String,
    val artist: String,
    val album: CatalogAlbum,
    val title: String,
) : TaskCommand

@Serializable
internal data object RemoveMissingSongsCommand : TaskCommand

@Serializable
internal data object UpdateDurationsCommand : TaskCommand

@Serializable
internal sealed interface TaskUpdate

/**
 * Its songs without artwork, which the lists load on their own.
 */
@Serializable
internal data class AutoSortUpdate(val state: AutoSort.State) : TaskUpdate

@Serializable
internal data class MergeUpdate(val progress: Pair<Int, Int>?) : TaskUpdate

@Serializable
internal data class DownloadsUpdate(val status: Map<String, SongDownloader.Status>) : TaskUpdate

/**
 * Only to the client that asked for the duplicates, like [DuplicatesFound] and [MaintenanceUpdate].
 */
@Serializable
internal data class DuplicatesProgress(
    val step: String,
    val done: Int,
    val total: Int,
) : TaskUpdate

@Serializable
internal data class DuplicatesFound(val groups: List<DuplicateGroup>) : TaskUpdate

@Serializable
internal data class MaintenanceUpdate(val progress: SongRepository.Progress) : TaskUpdate

@OptIn(ExperimentalCoroutinesApi::class)
internal object LibraryTasks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val autoSort = MutableStateFlow(AutoSort.State())
    val merging = MutableStateFlow<Pair<Int, Int>?>(null)
    val downloads = MutableStateFlow(emptyMap<String, SongDownloader.Status>())

    // Answers to this client's own requests
    private val answers = MutableSharedFlow<TaskUpdate>(extraBufferCapacity = 64)

    fun onUpdate(update: TaskUpdate) {
        when (update) {
            is AutoSortUpdate -> {
                val doneSorting = autoSort.value.sorting != null && update.state.sorting == null
                autoSort.value = update.state
                reloadIf(doneSorting)
            }

            is MergeUpdate -> {
                val doneMerging = merging.value != null && update.progress == null
                merging.value = update.progress
                reloadIf(doneMerging)
            }

            is DownloadsUpdate -> {
                val ended = downloads.value.filterValues { it == SongDownloader.Status.Running }.keys.any {
                    update.status[it] != SongDownloader.Status.Running
                }
                downloads.value = update.status
                reloadIf(ended)
            }

            else -> answers.tryEmit(update)
        }
    }

    fun send(command: TaskCommand) = NetworkManager.sendTask(command)

    suspend fun findDuplicates(onProgress: (step: String, done: Int, total: Int) -> Unit): List<DuplicateGroup> =
        coroutineScope {
            // Listening before asking, so the answer can't come first
            val found = async(start = CoroutineStart.UNDISPATCHED) {
                untilDisconnected(
                    flow = answers,
                    end = DuplicatesFound(emptyList())
                ).transformWhile { update ->
                    if (update is DuplicatesProgress) {
                        onProgress(update.step, update.done, update.total)
                    }
                    if (update is DuplicatesFound) {
                        emit(update.groups)
                    }
                    update !is DuplicatesFound
                }.first()
            }
            send(FindDuplicatesCommand)
            found.await()
        }

    /**
     * The progress of a maintenance [command] on the server, until it's complete.
     */
    fun maintenance(command: TaskCommand): Flow<SongRepository.Progress> = channelFlow {
        launch(start = CoroutineStart.UNDISPATCHED) {
            untilDisconnected(
                flow = answers.filterIsInstance<MaintenanceUpdate>(),
                end = MaintenanceUpdate(
                    SongRepository.Progress(
                        isComplete = true
                    )
                )
            ).transformWhile {
                emit(it.progress)
                !it.progress.isComplete
            }.collect { send(it) }
        }
        this@LibraryTasks.send(command)
    }

    /**
     * [local] on this device, the server's [remote] while connected.
     */
    fun <T> localOrRemote(
        local: StateFlow<T>,
        remote: StateFlow<T>,
    ): StateFlow<T> = NetworkManager.isConnected.flatMapLatest { connected ->
        if (connected) {
            remote
        } else {
            local
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = local.value
    )

    // Without a connection no answer comes: the wait ends with [end]
    private fun <T> untilDisconnected(
        flow: Flow<T>,
        end: T,
    ) = merge(
        flow,
        NetworkManager.isConnected.filter { !it }.map { end }
    )

    // A task that changed the server's songs ended: the lists show them again
    private fun reloadIf(changed: Boolean) {
        if (changed) {
            MainViewModel.loadSongs()
        }
    }
}
