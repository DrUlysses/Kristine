package dr.ulysses.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.path.createTempDirectory

actual val canDownloadSongs = true

// shortcut: java.lang.ProcessBuilder, as Kotlin has no API to run a program on the JVM
actual suspend fun downloadAudio(
    query: String,
    fileName: String,
): String = withContext(Dispatchers.IO) {
    val folder = File(songsFolder())
    val ytDlp = SettingsRepository.get(SettingKey.YtDlpPath)?.value?.takeIf { it.isNotBlank() } ?: "yt-dlp"
    // Usually another copy of the same song, which then already has its own entry
    folder.listFiles()?.find { it.nameWithoutExtension == fileName }?.let {
        error("${it.name} already exists in $folder")
    }
    // yt-dlp works in a folder of its own: in the music folder it would take an existing file of the same name
    // for its download, and change or delete it
    val work = createTempDirectory("kristine-download").toFile()
    try {
        val process = ProcessBuilder(
            ytDlp,
            "--extract-audio",
            "--audio-format", "mp3",
            "--embed-thumbnail", // becomes the artwork; needs ffmpeg, like --extract-audio
            "--no-playlist",
            // A network with broken IPv6 otherwise hangs on the first request; YouTube serves IPv4 everywhere
            "--force-ipv4",
            "--socket-timeout", "30",
            "--quiet",
            "--no-warnings",
            "--paths", work.absolutePath,
            "--output", "$fileName.%(ext)s",
            // The final file's path, printed once converted; --print alone would only simulate
            "--print", "after_move:filepath",
            "--no-simulate",
            query
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0) { "yt-dlp failed: $output" }
        val downloaded = File(output.lines().last())
        // copyTo refuses to overwrite, should a file of that name have appeared meanwhile
        val target = downloaded.copyTo(File(folder, downloaded.name))
        "file:///" + target.absolutePath
    } finally {
        work.deleteRecursively()
    }
}
