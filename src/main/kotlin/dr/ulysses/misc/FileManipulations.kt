package dr.ulysses.misc

import dr.ulysses.entities.DtoSong
import java.io.File
import javax.sound.sampled.*

fun File.toDtoSong(): DtoSong? {
    val tempFile = if ( this.endsWith(".mp3") ) this else this.toMp3()
    val name = this.extractTitleAlbumFromName()
//    return tags?.let {
//        DtoSong(
//            title = name.first,
//            album = it.album,
//            artist = name.second,
//            duration = mp3File.lengthInSeconds.toInt(),
//            path = tempFile.path,
//            tags = listOf(),
//            text = null,
//            status = "Added",
//        )
//    }
    return null
}

fun File.extractTitleAlbumFromName(): Pair<String, String> =
    this.name.split(" - ").let {
        Pair(it[0], it[1])
    }

fun File.toMp3(): File {
    val audioInputStream = AudioSystem.getAudioInputStream(this)
    val format = audioInputStream.format
    val newFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.sampleRate, 16, format.channels,
        format.channels * 2, format.sampleRate, false)
    val directory = File("music")
    if (!directory.exists()) {
        directory.mkdir()
    }
    val res = File(directory, this.name + ".mp3").apply { createNewFile() }
    val newAudioInputStream = AudioSystem.getAudioInputStream(newFormat, audioInputStream)
    AudioSystem.write(newAudioInputStream, AudioFileFormat.Type.WAVE, res)
    return res
}
