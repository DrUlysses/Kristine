package dr.ulysses.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dr.ulysses.ui.elements.SettingsDropdownEntry
import kotlinx.coroutines.launch
import kristine.shared.generated.resources.Res
import kristine.shared.generated.resources.back
import kristine.shared.generated.resources.more
import org.jetbrains.compose.resources.stringResource

/** One bar: the tabs, or a detail view's back button and title, with the menu at its end. */
@Composable
fun TabMenu(
    pagerState: PagerState,
    topText: String? = null,
    tabs: List<String> = emptyList(),
    navigateUp: () -> Unit = {},
    menuEntries: List<SettingsDropdownEntry> = emptyList(),
) {
    val menuExpanded = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isDetail = !topText.isNullOrEmpty()
    // The background reaches under the status bar; the content stays below it
    Surface(
        color = if (isDetail) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isDetail) {
                IconButton(
                    onClick = navigateUp
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back)
                    )
                }
                Text(
                    text = topText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                PrimaryTabRow(
                    containerColor = Color.Transparent,
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            selected = pagerState.currentPage == index,
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        )
                    }
                }
            }
            Box {
                IconButton(
                    onClick = { menuExpanded.value = !menuExpanded.value }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.more)
                    )
                }
                SettingsDropdown(
                    menuEntries = menuEntries,
                    menuExpanded = menuExpanded
                )
            }
        }
    }
}
