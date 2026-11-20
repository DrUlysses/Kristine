package dr.ulysses.entities

/**
 * The song's album, or null when its tags don't name one.
 */
fun albumOf(song: Song) = song.album?.trim()?.takeUnless {
    it.isEmpty() || it.equals(
        other = "Unknown album",
        ignoreCase = true
    )
}

/**
 * The song's album without what tells its editions and releases apart, so they count as one album: "Album (Deluxe)",
 * "AlbumDeluxe", "Album (CD Single) CD2" and "Album [Disc 1]" are all "Album". Null when its tags don't name one.
 */
fun albumTitleOf(song: Song) = albumOf(song)?.let { album ->
    album.replace(
        regex = EDITION_IN_BRACKETS,
        replacement = ""
    ).replace(
        regex = EDITION_AT_END,
        replacement = ""
    ).trim().ifEmpty { album }
}

/**
 * The song's main artist, whose page, albums and folder it belongs to: "Artist, Guest" and "Artist feat. Guest" are
 * Artist's. Not split at "&", which is part of band names like "A & B".
 */
fun artistOf(song: Song) = song.artist.split(
    regex = FEATURING,
    limit = 2
).first().trim()

/**
 * The artists credited besides [artistOf]: "Guest" for "Artist, Guest".
 */
fun featuredOf(song: Song) = song.artist.split(FEATURING).drop(1).map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Equal for the spellings of one name: case, punctuation and "&" for "and" aside.
 */
fun nameKey(name: String) = name.lowercase().replace(
    oldValue = "&",
    newValue = "and"
).filter { it.isLetterOrDigit() }

/**
 * The spelling most of [names] use.
 */
fun mostCommon(names: List<String>) = names.groupingBy { it }.eachCount().maxBy { it.value }.key

private val FEATURING = Regex(
    pattern = """,\s*|\s+(?:feat\.?|ft\.?|featuring)\s+""",
    option = RegexOption.IGNORE_CASE
)

private val EDITION_IN_BRACKETS = Regex(
    pattern = """\s*[(\[][^)\]]*\b(edition|deluxe|bonus|remaster\w*|cd|disc|vinyl|lp|single|promo|ep|version|special|limited|expanded|japanese)\b[^)\]]*[)\]]""",
    option = RegexOption.IGNORE_CASE
)

// Also glued on by tag editors that drop the brackets: "AlbumSpecial Edition", "Album2010 Edition"
private val EDITION_AT_END = Regex(
    pattern = """\s*(\b(cd|disc)\s*\d+\b.*|(\d{4}|special|super|limited|expanded)?\s*(edition|deluxe)(\s+edition)?)$""",
    option = RegexOption.IGNORE_CASE
)
