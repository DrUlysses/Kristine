package dr.ulysses.ui.elements

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable

// Android lists show the system's own scroll indicator while scrolling
@Composable
actual fun BoxScope.ListScrollbar(state: LazyListState) = Unit
