package dr.ulysses.entities

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dr.ulysses.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.io.File

actual val canDownloadSongs = true

private val setup = Mutex()

@Volatile
private var isReady = false

/**
 * Unpacks yt-dlp, Python and ffmpeg on the first download, then brings yt-dlp up to date: YouTube changes often,
 * and the bundled yt-dlp soon can't read it. An update that fails leaves the bundled one.
 */
private suspend fun ensureYtDlp(context: Context) = setup.withLock {
    if (isReady) {
        return@withLock
    }
    YoutubeDL.getInstance().init(context)
    FFmpeg.getInstance().init(context)
    runCatching {
        YoutubeDL.getInstance().updateYoutubeDL(
            appContext = context,
            updateChannel = YoutubeDL.UpdateChannel.STABLE
        )
    }
        .onFailure { currentCoroutineContext().ensureActive() }
        .onFailure { Logger.w(it) { "Updating yt-dlp failed; the bundled one stays" } }
    isReady = true
}

actual suspend fun downloadAudio(
    query: String,
    fileName: String,
): String = withContext(Dispatchers.IO) {
    val folder = File(musicFolder() ?: error("This device has no music folder"))
    // Usually another copy of the same song, which then already has its own entry
    folder.listFiles()?.find { it.nameWithoutExtension == fileName }?.let {
        error("${it.name} already exists in $folder")
    }
    inWorkFolder { work ->
        val request = ytDlpRequest(
            target = query,
            work = work
        )
            .addOption("--no-playlist")
            .addOption(
                option = "--output",
                argument = "$fileName.%(ext)s"
            )
        val response = YoutubeDL.getInstance().execute(request)
        check(response.exitCode == 0) { "yt-dlp failed: ${response.err.ifBlank { response.out }}" }
        val downloaded = work.listFiles()?.singleOrNull { it.extension == "mp3" }
            ?: error("yt-dlp left no mp3: ${response.out}")
        intoMusicFolder(
            file = downloaded,
            folder = folder
        ) ?: error("${downloaded.name} couldn't be written to $folder")
    }
}

actual suspend fun youTubeVideos(link: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
    val context: Context by inject(Context::class.java)
    ensureYtDlp(context)
    val request = YoutubeDLRequest(link)
        .addOption("--flat-playlist")
        .addOption("--yes-playlist")
        .addOption("--force-ipv4")
        .addOption("--no-warnings")
        .addOption(
            option = "--print",
            argument = VIDEO_LINE
        )
    val response = YoutubeDL.getInstance().execute(request)
    check(response.exitCode == 0) { "yt-dlp failed: ${response.err.ifBlank { response.out }}" }
    youTubeVideosOf(response.out)
}

// yt-dlp works in a folder of its own: in the music folder it would take an existing file of the same name for its
// download, and change or delete it
private suspend fun <T> inWorkFolder(block: suspend (File) -> T): T {
    val context: Context by inject(Context::class.java)
    ensureYtDlp(context)
    val work = File(context.cacheDir, "download-${System.nanoTime()}").apply { mkdirs() }
    try {
        return block(work)
    } finally {
        work.deleteRecursively()
    }
}

/**
 * A yt-dlp request for [target] that saves mp3 files with their thumbnail into [work].
 */
private fun ytDlpRequest(
    target: String,
    work: File,
) = YoutubeDLRequest(target)
    .addOption("--extract-audio")
    .addOption(
        option = "--audio-format",
        argument = "mp3"
    )
    // Becomes the artwork
    .addOption("--embed-thumbnail")
    // YouTube's title and channel, until the song is sorted
    .addOption("--embed-metadata")
    // A network with broken IPv6 otherwise hangs on the first request; YouTube serves IPv4 everywhere
    .addOption("--force-ipv4")
    .addOption(
        option = "--socket-timeout",
        argument = 30
    )
    .addOption("--quiet")
    .addOption("--no-warnings")
    .addOption(
        option = "--paths",
        argument = work.absolutePath
    )

// Through MediaStore, which otherwise doesn't know the file and a scan drops it; null when a file of that name is there
private suspend fun intoMusicFolder(
    file: File,
    folder: File,
): String? = writeSongFile(
    file = File(folder, file.name).absolutePath,
    bytes = file.readBytes()
)
