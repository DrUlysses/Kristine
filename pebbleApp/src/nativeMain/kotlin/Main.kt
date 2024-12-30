@file:OptIn(ExperimentalForeignApi::class)

import basalt.*
import kotlinx.cinterop.*

fun getCallbackPointer(): CPointer<CFunction<(CPointer<out CPointed>?) -> Unit>>? {
    // Convert the Kotlin function to a C function pointer
    return staticCFunction { pointer ->
        println("Callback invoked with pointer: $pointer")
    }
}

fun main() {
    val window = window_create()
    window_set_click_config_provider(
        window = window,
        click_config_provider = getCallbackPointer()
    )
    app_log(
        log_level = APP_LOG_LEVEL_INFO.toUByte(),
        src_line_number = 42,
        src_filename = "Main.kt",
        fmt = "Hi from Pebble!",
    )
    app_event_loop()
    window_destroy(window)
}
