package dr.ulysses.ui.elements

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable

/** A draggable scrollbar at the end of the box, for a list in it; nothing where the platform shows its own. */
@Composable
expect fun BoxScope.ListScrollbar(state: LazyListState)
