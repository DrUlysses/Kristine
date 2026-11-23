package dr.ulysses.entities

import dr.ulysses.ui.elements.songs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Where a running task is shown in full.
 */
enum class TaskScreen {
    Sync,
    AutoSort,
    Duplicates,
    MergeNames,
    Unsorted,
}

/**
 * A long task, shown in the menu (and on Android in a notification) while it runs, also when its screen is closed.
 */
data class RunningTask(
    val name: String,
    val screen: TaskScreen,
    val done: Int = 0,
    val total: Int = 0,
) {
    val text
        get() = if (total > 0) {
            "$name $done of $total"
        } else {
            name
        }
}

/**
 * Runs the maintenance passes over the library in the app's scope, so leaving their screen doesn't end them.
 */
object Maintenance {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The running pass's name and progress; null while none runs.
     */
    val running: StateFlow<Pair<String, SongRepository.Progress>?>
        field = MutableStateFlow<Pair<String, SongRepository.Progress>?>(null)

    /**
     * Collects [progress] as [name], unless a pass runs already; [onProgress] gets each step on the main thread.
     */
    fun run(
        name: String,
        progress: Flow<SongRepository.Progress>,
        onProgress: (SongRepository.Progress) -> Unit,
    ) {
        if (running.value != null) {
            return
        }
        running.value = name to SongRepository.Progress()
        scope.launch {
            try {
                progress.collect {
                    running.value = name to it
                    withContext(Dispatchers.Main) { onProgress(it) }
                }
            } finally {
                running.value = null
            }
        }
    }
}

object BackgroundTasks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val running: StateFlow<List<RunningTask>> = combine(
        combine(
            Sync.state,
            AutoSort.state,
            NameMerger.progress
        ) { sync, autoSort, merge ->
            listOfNotNull(
                sync.step?.let {
                    RunningTask(
                        name = "Sync: $it",
                        screen = TaskScreen.Sync,
                        done = sync.done,
                        total = sync.total
                    )
                },
                autoSort.sorting?.let { (done, total) ->
                    RunningTask(
                        name = "Auto-sort: sorting",
                        screen = TaskScreen.AutoSort,
                        done = done,
                        total = total
                    )
                } ?: RunningTask(
                    name = "Auto-sort: looking up",
                    screen = TaskScreen.AutoSort,
                    done = autoSort.checked,
                    total = autoSort.total
                ).takeIf { autoSort.isRunning },
                merge?.let { (done, total) ->
                    RunningTask(
                        name = "Merging names",
                        screen = TaskScreen.MergeNames,
                        done = done,
                        total = total
                    )
                }
            )
        },
        combine(
            SongDownloader.status,
            DuplicateSearch.state,
            Maintenance.running
        ) { downloads, duplicates, maintenance ->
            val downloading = downloads.count { it.value == SongDownloader.Status.Running }
            listOfNotNull(
                RunningTask(
                    name = "Downloading ${songs(downloading)}",
                    screen = TaskScreen.Unsorted
                ).takeIf { downloading > 0 },
                duplicates.step?.let {
                    RunningTask(
                        name = "Duplicates: $it",
                        screen = TaskScreen.Duplicates,
                        done = duplicates.done,
                        total = duplicates.total
                    )
                },
                maintenance?.let { (name, progress) ->
                    RunningTask(
                        name = name,
                        screen = TaskScreen.Unsorted,
                        done = progress.processed,
                        total = progress.total
                    )
                }
            )
        }
    ) { first, second -> first + second }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
}
