package dr.ulysses.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.back
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabMenu(
    pagerState: PagerState,
    tabs: List<String> = listOf(
        "Artists",
        "Songs",
        "Albums"
    ),
    topText: String? = null,
    modifier: Modifier = Modifier,
    navigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
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
        ScrollableTabRow(
            containerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            selectedTabIndex = pagerState.currentPage,
        ) {
            for (tab in tabs) {
                Tab(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(tabs.indexOf(tab))
                        }
                    },
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
                                    color = if (pagerState.currentPage == tabs.indexOf(tab))
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.background
                                )
                        )
                        Text(
                            text = tab,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
}
