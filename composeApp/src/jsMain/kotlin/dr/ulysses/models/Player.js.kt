package dr.ulysses.models

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
    TODO("Not yet implemented")
}

actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
}

actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
}

actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
}
