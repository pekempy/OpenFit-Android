package com.openfit.mobile.ui.metrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.ui.activity.ActivityScreen
import com.openfit.mobile.ui.body.BodyScreen
import com.openfit.mobile.ui.health.HealthScreen
import com.openfit.mobile.ui.calendar.CalendarScreen
import com.openfit.mobile.ui.sleep.SleepScreen
import com.openfit.mobile.ui.today.TodayUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow

enum class MetricSubTab(val title: String, val icon: ImageVector) {
    ACTIVITY("Activity", Icons.Filled.DirectionsWalk),
    SLEEP("Sleep",    Icons.Filled.Bedtime),
    VITALS("Vitals",  Icons.Filled.Favorite),
    BODY("Body",      Icons.Filled.MonitorWeight),
    HISTORY("History",Icons.Filled.CalendarMonth),
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

    val tabRevealKeys = remember { mutableStateMapOf<Int, Long>() }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
            .filter { (scrolling, _) -> !scrolling }
            .map { (_, page) -> page }
            .distinctUntilChanged()
            .collect { page -> tabRevealKeys[page] = System.currentTimeMillis() }
    }

    val isRefreshing = (state as? TodayUiState.Success)?.isRefreshing ?: false
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                edgePadding = 16.dp,
                divider = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                tab.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val revealKey = tabRevealKeys[page] ?: 0L
                when (tabs[page]) {
                    MetricSubTab.ACTIVITY -> ActivityScreen(container, state, onRefresh, revealKey)
                    MetricSubTab.SLEEP    -> SleepScreen(container, state, onRefresh, revealKey)
                    MetricSubTab.VITALS   -> HealthScreen(container, state, onRefresh, revealKey)
                    MetricSubTab.BODY     -> BodyScreen(container, state, onRefresh, revealKey)
                    MetricSubTab.HISTORY  -> CalendarScreen(container = container)
                }
            }
        }
    }
}
