package dr.ulysses.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.network.ServerInfo
import dr.ulysses.ui.elements.LocalServerCardEntry

@Composable
fun LocalServerCard(
    localServer: ServerInfo,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your Server",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    LocalServerCardEntry(
                        text = "localhost",
                    )
                    localServer.addresses.forEach { address ->
                        LocalServerCardEntry(
                            text = address,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    LocalServerCardEntry(
                        text = localServer.port.toString(),
                    )
                }
            }
        }
    }
}
