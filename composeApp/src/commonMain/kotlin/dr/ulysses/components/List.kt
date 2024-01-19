package dr.ulysses.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.elements.SongListEntry

@Composable
fun List(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        val listState = rememberLazyListState()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            content = {
                itemsIndexed(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")) { index, item ->
                    SongListEntry()
                }
            },
        )

    }
}
