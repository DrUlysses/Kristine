package dr.ulysses.player

actual fun setPlayListOnDevice(paths: List<String>) {
}

actual fun pauseCurrentSongOnDevice() {
}

actual fun resumeCurrentSongOnDevice() {
}

actual fun stopCurrentSongOnDevice() {
}

actual fun seekToOnDevice(position: Int) {
}

actual fun isPlayingOnDevice(): Boolean {
    return false
}

actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
}

actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
}

actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
}

actual fun playNextOnDevice() {
}

actual fun playPreviousOnDevice() {
}
