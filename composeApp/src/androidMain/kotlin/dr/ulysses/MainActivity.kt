package dr.ulysses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dr.ulysses.inject.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storageDir = filesDir.path
        setContent {
            App(DriverFactory(this))
        }

        startKoin {
            androidContext(this@MainActivity)
            modules(appModule())
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(DriverFactory(MainActivity()))
}
