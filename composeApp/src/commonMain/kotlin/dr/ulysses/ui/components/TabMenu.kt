package dr.ulysses.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dr.ulysses.ui.views.Navigation
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
    modifier: Modifier = Modifier,
    navigateUp: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    Row {
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            if (!topText.isNullOrEmpty())
                TopAppBar(
                    title = {
                        Text(text = topText)
                    },
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = modifier,
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
                        .height(50.dp),
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    for (tab in Navigation.shownEntries) {
                        Tab(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(Navigation.entries.indexOf(tab))
                                }
                            },
                            selected = pagerState.currentPage == Navigation.entries.indexOf(tab),
                            enabled = true,
                            text = {
                                Text(
                                    text = tab.name,
                                    style = MaterialTheme.typography.bodyLarge,
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
                    .height(50.dp)
            ) {
                IconButton(
                    onClick = { menuExpanded = !menuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.more)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.25f)
                ) {
                    for (tab in Navigation.entries) {
                        DropdownMenuItem(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(Navigation.entries.indexOf(tab))
                                }
                                menuExpanded = false
                            },
                            text = {
                                Text(
                                    text = tab.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
