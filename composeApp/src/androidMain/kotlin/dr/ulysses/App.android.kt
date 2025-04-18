package dr.ulysses

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.net.toUri
import dr.ulysses.inject.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent

class AndroidApp : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        val storageDir = filesDir.path
        initKoin {
            androidContext(this@AndroidApp)
            androidLogger()
        }
    }
}

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

internal actual fun openUrl(url: String?) {
    val uri = url?.toUri() ?: return
    val intent = Intent().apply {
        action = Intent.ACTION_VIEW
        data = uri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidApp().startActivity(intent)
}
