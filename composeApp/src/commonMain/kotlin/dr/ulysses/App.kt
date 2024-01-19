package dr.ulysses

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import dr.ulysses.views.Main
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun App() = MaterialTheme(
    colorScheme = darkColorScheme()
) {
//        var showContent by remember { mutableStateOf(false) }
//        val greeting = remember { Greeting().greet() }
//        Column(
//            Modifier.fillMaxWidth(),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Button(onClick = { showContent = !showContent }) {
//                Text("Click")
//            }
//            AnimatedVisibility(showContent) {
//                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
//                    Image(painterResource("compose-multiplatform.xml"), null)
//                    Text("Compose: $greeting")
//                }
//            }
//        }
    Main()
}
