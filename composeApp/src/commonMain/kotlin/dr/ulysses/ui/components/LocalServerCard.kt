package dr.ulysses.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.network.ServerInfo

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
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row {
                Column {
                    TextField(
                        value = "localhost",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.padding(bottom = 8.dp),
                        singleLine = true,
                    )
                    localServer.addresses.forEach { address ->
                        TextField(
                            value = address,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.padding(bottom = 8.dp),
                            singleLine = true,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    TextField(
                        value = localServer.port.toString(),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.padding(bottom = 8.dp),
                        singleLine = true,
                    )
                }
            }
        }
    }
}
