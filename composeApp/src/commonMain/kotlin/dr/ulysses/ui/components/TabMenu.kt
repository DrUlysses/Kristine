package dr.ulysses.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TabMenu(
    tabs: List<Pair<String, Boolean>> = listOf("Artists" to false, "Songs" to true, "Albums" to false),
) {
    TabRow(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        selectedTabIndex = 2,
    ) {
        for (tab in tabs) {
            Tab(
                onClick = { /*TODO*/ },
                selected = false,
                enabled = true,
            ) {
                Column(
                    Modifier
                        .padding(10.dp)
                        .height(50.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(
                                color = if (tab.second) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.background
                            )
                    )
                    Text(
                        text = tab.first,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}
