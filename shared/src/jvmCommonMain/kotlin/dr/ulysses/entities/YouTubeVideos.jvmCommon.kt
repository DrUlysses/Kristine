package dr.ulysses.entities

/**
 * yt-dlp's `--print` template that [youTubeVideosOf] reads: the video's id, a tab, and "Channel - Title".
 */
internal const val VIDEO_LINE = "%(id)s\t%(channel,uploader|)s - %(title)s"

/**
 * The videos in yt-dlp's [output] of [VIDEO_LINE]s; other lines, like errors, are skipped.
 */
internal fun youTubeVideosOf(output: String): List<YouTubeVideo> = output.lines().mapNotNull { line ->
    val parts = line.split(
        '\t',
        limit = 2
    )
    if (parts.size != 2 || parts[0].isBlank()) {
        return@mapNotNull null
    }
    YouTubeVideo(
        url = "https://www.youtube.com/watch?v=${parts[0]}",
        name = parts[1].trim()
    )
}
