package dr.ulysses.network

import android.content.Context
import org.koin.java.KoinJavaComponent.inject

internal actual fun appDataDirectory(): String {
    val context: Context by inject(Context::class.java)
    return context.filesDir.path
}
