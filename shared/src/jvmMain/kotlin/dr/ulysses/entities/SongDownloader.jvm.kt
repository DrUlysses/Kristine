package dr.ulysses.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.path.createTempDirectory

actual val canDownloadSongs = true

actual suspend fun downloadAudio(
    query: String,
    fileName: String,
): String = withContext(Dispatchers.IO) {
    val folder = File(songsFolder())
    // Usually another copy of the same song, which then already has its own entry
    folder.listFiles()?.find { it.nameWithoutExtension == fileName }?.let {
        error("${it.name} already exists in $folder")
    }
    // yt-dlp works in a folder of its own: in the music folder it would take an existing file of the same name
    // for its download, and change or delete it
    val work = createTempDirectory("kristine-download").toFile()
    try {
        val (exitCode, output) = ytDlp(
            "--extract-audio",
            "--audio-format", "mp3",
            "--embed-thumbnail", // becomes the artwork; needs ffmpeg, like --extract-audio
            "--embed-metadata", // YouTube's title and channel, until the song is sorted
            "--no-playlist",
            "--quiet",
            "--paths", work.absolutePath,
            "--output", "$fileName.%(ext)s",
            // The final file's path, printed once converted; --print alone would only simulate
            "--print", "after_move:filepath",
            "--no-simulate",
            query
        )
        check(exitCode == 0) { "yt-dlp failed: $output" }
        val downloaded = File(output.lines().last())
        // copyTo refuses to overwrite, should a file of that name have appeared meanwhile
        val target = downloaded.copyTo(File(folder, downloaded.name))
        "file:///" + target.absolutePath
    } finally {
        work.deleteRecursively()
    }
}

actual suspend fun youTubeVideos(link: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
    val (exitCode, output) = ytDlp(
        "--flat-playlist",
        "--yes-playlist",
        "--print", VIDEO_LINE,
        link
    )
    check(exitCode == 0) { "yt-dlp failed: $output" }
    youTubeVideosOf(output)
}

/**
 * Runs yt-dlp with [arguments]; its exit code, and its output with the errors.
 */
// shortcut: java.lang.ProcessBuilder, as Kotlin has no API to run a program on the JVM
private suspend fun ytDlp(vararg arguments: String): Pair<Int, String> {
    val ytDlp = SettingsRepository.get(SettingKey.YtDlpPath)?.value?.takeIf { it.isNotBlank() } ?: "yt-dlp"
    val process = ProcessBuilder(
        ytDlp,
        // A network with broken IPv6 otherwise hangs on the first request; YouTube serves IPv4 everywhere
        "--force-ipv4",
        "--socket-timeout", "30",
        "--no-warnings",
        *arguments
    ).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    return process.waitFor() to output
}
