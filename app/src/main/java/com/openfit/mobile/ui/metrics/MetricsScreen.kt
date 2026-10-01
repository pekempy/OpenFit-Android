package com.openfit.mobile.ui.metrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.ui.activity.ActivityScreen
import com.openfit.mobile.ui.body.BodyScreen
import com.openfit.mobile.ui.health.HealthScreen
import com.openfit.mobile.ui.sleep.SleepScreen
import com.openfit.mobile.ui.today.TodayUiState
import kotlinx.coroutines.launch

enum class MetricSubTab(val title: String, val icon: ImageVector) {
    ACTIVITY("Activity", Icons.Filled.DirectionsWalk),
    SLEEP("Sleep", Icons.Filled.Bedtime),
    VITALS("Vitals", Icons.Filled.Favorite),
    BODY("Body", Icons.Filled.MonitorWeight),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(
    container: AppContainer,
    state: TodayUiState,
    onRefresh: () -> Unit,
    initialTab: MetricSubTab = MetricSubTab.ACTIVITY,
) {
    val tabs = MetricSubTab.entries
    val pagerState = rememberPagerState(
        initialPage = initialTab.ordinal,
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage

    Scaffold(
        topBar = { TopAppBar(title = { Text("Metrics") }) },
    ) { padding ->
        val isRefreshing = (state as? TodayUiState.Success)?.isRefreshing ?: false
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRow(
                    selectedTabIndex = currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = currentPage == index
                        Tab(
                            selected = isSelected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(20.dp)) },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (tabs[page]) {
                        MetricSubTab.ACTIVITY -> ActivityScreen(container, state, onRefresh)
                        MetricSubTab.SLEEP    -> SleepScreen(container, state, onRefresh)
                        MetricSubTab.VITALS   -> HealthScreen(container, state, onRefresh)
                        MetricSubTab.BODY     -> BodyScreen(container, state, onRefresh)
                    }
                }
            }
        }
    }
}
