package dr.ulysses.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dr.ulysses.ui.elements.SettingsDropdownEntry
import kotlinx.coroutines.launch
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.back
import kristine.composeapp.generated.resources.more
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabMenu(
    pagerState: PagerState,
    topText: String? = null,
    tabs: Map<Int, String> = emptyMap(),
    navigateUp: () -> Unit = {},
    menuEntries: List<SettingsDropdownEntry> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    val menuExpanded = mutableStateOf(false)
    Row {
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            if (!topText.isNullOrEmpty())
                TopAppBar(
                    title = {
                        Text(
                            text = topText,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    },
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    navigationIcon = {
                        IconButton(onClick = navigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back)
                            )
                        }
                    }
                )
            else
                TabRow(
                    containerColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    for (tab in tabs) {
                        Tab(
                            onClick = {
                                scope.launch {
                                    pagerState.scrollToPage(tab.key)
                                }
                            },
                            selected = pagerState.currentPage == tab.key,
                            enabled = true,
                            text = {
                                Text(
                                    text = tab.value,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        )
                    }
                }

        }
        Column(
            modifier = Modifier
                .weight(0.1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                IconButton(
                    onClick = { menuExpanded.value = !(menuExpanded.value) },
                    modifier = Modifier.fillMaxWidth()
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
