package dr.ulysses

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import org.koin.mp.KoinPlatform

internal actual fun openUrl(url: String?) {
    val uri = url?.toUri() ?: return
    val intent = Intent().apply {
        action = Intent.ACTION_VIEW
        data = uri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // The app Context is registered by androidContext() in AndroidApp (androidApp module)
    KoinPlatform.getKoin().get<Context>().startActivity(intent)
}
