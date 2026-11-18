package dr.ulysses.network

import android.content.Context
import android.net.wifi.WifiManager
import org.koin.java.KoinJavaComponent.inject

internal actual fun acquireMulticastLock(): () -> Unit {
    val context: Context by inject(Context::class.java)
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return {}
    val lock = wifi.createMulticastLock("kristine-discovery").apply {
        setReferenceCounted(false)
        acquire()
    }
    return { lock.release() }
}
