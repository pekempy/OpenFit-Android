@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.openfit.mobile.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.settings.AppUnitSettings
import com.openfit.mobile.data.settings.UserHealthGoals
import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.model.ExerciseSession
import com.openfit.mobile.model.SleepSession
import com.openfit.mobile.ui.charts.ChartColors
import com.openfit.mobile.ui.common.SimpleViewModelFactory
import com.openfit.mobile.ui.common.UnitFormatter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ═════════════════════════════════════════════════════════════════════════════
// Root screen
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CalendarScreen(container: AppContainer) {
    val vm: CalendarViewModel = viewModel(
        factory = SimpleViewModelFactory { CalendarViewModel(container) }
    )
    val calendarState by vm.calendarState.collectAsState()
    val selectedDate  by vm.selectedDate.collectAsState()
    val dayDetail     by vm.dayDetail.collectAsState()
    val viewMode      by vm.viewMode.collectAsState()
    val settings      by container.settingsRepository.settingsFlow.collectAsState(initial = null)
    val isBackfilling by vm.isBackfilling.collectAsState()
    val weekSnapshots by vm.weekSnapshots.collectAsState()

    val today     = remember { LocalDate.now() }
    val thisMonth = remember { YearMonth.now() }
    val scope     = rememberCoroutineScope()

    // Dismiss banner once backfill has actually loaded enough history
    var showDataLimitBanner by remember { mutableStateOf(true) }

    // ── Pagers ───────────────────────────────────────────────────────────────
    // 120 months back (10 years) as page 0; current month = page 119.
    val monthTotalPages = 120
    val monthStartPage  = monthTotalPages - 1
    val monthPager = rememberPagerState(initialPage = monthStartPage) { monthTotalPages }
    fun pageToMonth(page: Int) = thisMonth.minusMonths((monthStartPage - page).toLong())

    // 520 weeks back (10 years); current week = page 519.
    val weekTotalPages = 520
    val weekStartPage  = weekTotalPages - 1
    val weekPager = rememberPagerState(initialPage = weekStartPage) { weekTotalPages }
    val currentWeekMonday = remember {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    fun pageToWeekStart(page: Int) =
        currentWeekMonday.minusWeeks((weekStartPage - page).toLong())

    // Derived display values for the header (read without triggering pager recompose)
    val displayedMonth by remember {
        derivedStateOf { pageToMonth(monthPager.currentPage) }
    }
    val displayedWeekStart by remember {
        derivedStateOf { pageToWeekStart(weekPager.currentPage) }
    }

    // Load data when month pager settles
    LaunchedEffect(monthPager) {
        snapshotFlow { monthPager.isScrollInProgress to monthPager.currentPage }
            .filter { (scrolling, _) -> !scrolling }
            .collect { (_, page) -> vm.loadMonth(pageToMonth(page)) }
    }
    // Load data when week pager settles
    // Load data when week pager settles — also request snapshot data for the week
    LaunchedEffect(weekPager) {
        snapshotFlow { weekPager.isScrollInProgress to weekPager.currentPage }
            .filter { (scrolling, _) -> !scrolling }
            .collect { (_, page) ->
                val ws = pageToWeekStart(page)
                vm.loadMonth(YearMonth.from(ws))
                val we = ws.plusDays(6)
                if (YearMonth.from(we) != YearMonth.from(ws))
                    vm.loadMonth(YearMonth.from(we))
                vm.loadWeek(ws)
            }
    }

    // ── Day detail bottom sheet ───────────────────────────────────────────────
    val detail   = dayDetail
    val selected = selectedDate
    if (selected != null && detail !is DayDetailState.None) {
        DayDetailBottomSheet(
            date      = selected,
            state     = detail,
            units     = settings?.units ?: AppUnitSettings(),
            goals     = settings?.goals ?: UserHealthGoals(),
            onDismiss = { vm.clearSelection() },
        )
    }

    // ── Layout ───────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header: title + navigation + mode toggle + legend
        CalendarHeader(
            viewMode           = viewMode,
            displayedMonth     = displayedMonth,
            displayedWeekStart = displayedWeekStart,
            canGoForward = when (viewMode) {
                CalendarViewMode.MONTH -> displayedMonth < thisMonth
                CalendarViewMode.WEEK  -> displayedWeekStart < currentWeekMonday
            },
            onPrev = {
                scope.launch {
                    when (viewMode) {
                        CalendarViewMode.MONTH -> monthPager.animateScrollToPage(
                            monthPager.currentPage - 1, animationSpec = tween(280)
                        )
                        CalendarViewMode.WEEK  -> weekPager.animateScrollToPage(
                            weekPager.currentPage - 1, animationSpec = tween(280)
                        )
                    }
                }
            },
            onNext = {
                scope.launch {
                    when (viewMode) {
                        CalendarViewMode.MONTH -> if (monthPager.currentPage < monthStartPage)
                            monthPager.animateScrollToPage(monthPager.currentPage + 1, animationSpec = tween(280))
                        CalendarViewMode.WEEK  -> if (weekPager.currentPage < weekStartPage)
                            weekPager.animateScrollToPage(weekPager.currentPage + 1, animationSpec = tween(280))
                    }
                }
            },
            onModeChange = { vm.setViewMode(it) },
        )

        // Day-of-week header — only relevant in month grid view
        if (viewMode == CalendarViewMode.MONTH) DayOfWeekLabels()

        // Data limit banner: shown when HC history is short OR while backfill is active
        val dataAvailableSince = (calendarState as? CalendarUiState.Ready)?.dataAvailableSince
        if (showDataLimitBanner && (dataAvailableSince != null || isBackfilling)) {
            DataLimitBanner(
                dataAvailableSince = dataAvailableSince ?: LocalDate.now(),
                isBackfilling      = isBackfilling,
                onDismiss          = { showDataLimitBanner = false },
            )
        }

        // Thin progress stripe while a background month fetch runs
        // Progress stripe: shown while a foreground month fetch OR background backfill runs
        val isLoadingMonth = calendarState.let { it is CalendarUiState.Ready && it.isLoadingMonth }
        AnimatedVisibility(visible = isLoadingMonth || isBackfilling) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color    = if (isBackfilling && !isLoadingMonth)
                               MaterialTheme.colorScheme.secondary
                           else
                               MaterialTheme.colorScheme.primary,
            )
        }

        // Calendar body: month grid OR week strip, animated transition
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "calendarBody",
        ) { mode ->
            when (mode) {
                CalendarViewMode.MONTH -> HorizontalPager(
                    state = monthPager,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    MonthGrid(
                        month              = pageToMonth(page),
                        today              = today,
                        selectedDate       = selectedDate,
                        calendarState      = calendarState,
                        onDaySelect        = { vm.selectDate(it) },
                        dataAvailableSince = (calendarState as? CalendarUiState.Ready)?.dataAvailableSince,
                    )
                }

                CalendarViewMode.WEEK -> HorizontalPager(
                    state = weekPager,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    WeekStrip(
                        weekStart     = pageToWeekStart(page),
                        today         = today,
                        selectedDate  = selectedDate,
                        calendarState = calendarState,
                        weekSnapshots = weekSnapshots,
                        onDaySelect   = { vm.selectDate(it) },
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Header
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun CalendarHeader(
    viewMode: CalendarViewMode,
    displayedMonth: YearMonth,
    displayedWeekStart: LocalDate,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onModeChange: (CalendarViewMode) -> Unit,
) {
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val weekFmt  = remember { DateTimeFormatter.ofPattern("MMM d") }

    val title = when (viewMode) {
        CalendarViewMode.MONTH -> displayedMonth.format(monthFmt)
        CalendarViewMode.WEEK  -> {
            val s = displayedWeekStart.format(weekFmt)
            val e = displayedWeekStart.plusDays(6).format(weekFmt)
            "$s – $e"
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Row: prev · title · next
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(
                    imageVector        = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous",
                    tint               = MaterialTheme.colorScheme.onSurface,
                )
            }

            AnimatedContent(
                targetState  = title,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label        = "calTitle",
            ) { t ->
                Text(
                    text       = t,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            IconButton(onClick = onNext, enabled = canGoForward) {
                Icon(
                    imageVector        = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Next",
                    tint               = if (canGoForward)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        }

        // Mode toggle (Month / Week)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
        ) {
            CalendarViewMode.entries.forEachIndexed { idx, mode ->
                SegmentedButton(
                    selected = viewMode == mode,
                    onClick  = { onModeChange(mode) },
                    shape    = SegmentedButtonDefaults.itemShape(
                        index = idx,
                        count = CalendarViewMode.entries.size,
                    ),
                    label = {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Dot legend
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            LegendDot(color = ChartColors.Movement, label = "Steps")
            Spacer(Modifier.width(20.dp))
            LegendDot(color = ChartColors.Sleep,    label = "Sleep")
            Spacer(Modifier.width(20.dp))
            LegendDot(color = ChartColors.Heart,    label = "Heart Rate")
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown when Health Connect history is shorter than 2 years, to explain
 *  why older calendar months have no data and how to access full history. */
@Composable
private fun DataLimitBanner(
    dataAvailableSince: LocalDate,
    isBackfilling: Boolean,
    onDismiss: () -> Unit,
) {
    val formatter = remember { java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy") }
    val monthsOfData = remember(dataAvailableSince) {
        ChronoUnit.MONTHS.between(dataAvailableSince, LocalDate.now()).toInt()
    }
    // Only show if HC has less than 18 months of data.
    if (monthsOfData >= 18) return

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isBackfilling)
                        "⏳ Loading full history in the background…"
                    else
                        "📅 Health Connect data from ${dataAvailableSince.format(formatter)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = if (isBackfilling)
                        "Calendar dots fill in automatically. Keep the app open for faster backfill."
                    else
                        "Older data lives in Google Fit. Enable Google Health API in Settings → Data Source for full history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Day-of-week label row
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DayOfWeekLabels() {
    // Build labels from Monday to Sunday using locale-aware narrow names
    val labels = remember {
        (1..7).map { dow ->
            DayOfWeek.of(dow).getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        labels.forEach { label ->
            Text(
                text      = label,
                modifier  = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

// ═════════════════════════════════════════════════════════════════════════════
// Month grid (6 × 7)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    calendarState: CalendarUiState,
    onDaySelect: (LocalDate) -> Unit,
    dataAvailableSince: LocalDate? = null,
) {
    val indicators  = (calendarState as? CalendarUiState.Ready)?.indicators ?: emptyMap()
    val isFirstLoad = calendarState is CalendarUiState.Loading

    // Month is definitively before Health Connect's data range — show empty state.
    val isBeforeHcRange = dataAvailableSince != null &&
        month.atEndOfMonth() < dataAvailableSince
    if (isBeforeHcRange) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "No Health Connect data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "HC history starts ${dataAvailableSince!!.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    // Monday-based offset for the 1st of month (Mon = 0 … Sun = 6)
    val firstDow    = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val rows        = ((firstDow + daysInMonth) + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val idx        = row * 7 + col
                    val dayOfMonth = idx - firstDow + 1

                    if (dayOfMonth < 1 || dayOfMonth > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).height(58.dp))
                    } else {
                        val date      = month.atDay(dayOfMonth)
                        val isFuture  = date > today
                        val indicator = indicators[date.toString()]
                        val isLoading = isFirstLoad && indicator == null && !isFuture

                        if (isLoading) {
                            ShimmerDayCell(Modifier.weight(1f))
                        } else {
                            DayCell(
                                date      = date,
                                isToday   = date == today,
                                isSelected = date == selectedDate,
                                isFuture  = isFuture,
                                indicators = indicator,
                                onClick   = { if (!isFuture) onDaySelect(date) },
                                modifier  = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Week view — vertical list, Mon→Sun, stats on the right
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate?,
    calendarState: CalendarUiState,
    weekSnapshots: Map<String, com.openfit.mobile.model.DailySnapshot>,
    onDaySelect: (LocalDate) -> Unit,
) {
    val indicators = (calendarState as? CalendarUiState.Ready)?.indicators ?: emptyMap()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0..6).forEach { offset ->
            val date      = weekStart.plusDays(offset.toLong())
            val isFuture  = date > today
            val snapshot  = weekSnapshots[date.toString()]
            val indicator = indicators[date.toString()]

            WeekDayRow(
                date       = date,
                isToday    = date == today,
                isSelected = date == selectedDate,
                isFuture   = isFuture,
                snapshot   = snapshot,
                indicator  = indicator,
                onClick    = { if (!isFuture) onDaySelect(date) },
            )
        }
    }
}

@Composable
private fun WeekDayRow(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    snapshot: com.openfit.mobile.model.DailySnapshot?,
    indicator: DayIndicators?,
    onClick: () -> Unit,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> primary
            isToday    -> primary.copy(alpha = 0.10f)
            else       -> MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "weekRowBg",
    )
    val textColor = when {
        isSelected -> onPrimary
        isFuture   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
        isToday    -> primary
        else       -> MaterialTheme.colorScheme.onSurface
    }

    ElevatedCard(
        onClick   = { if (!isFuture) onClick() },
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.elevatedCardColors(containerColor = bgColor),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Left: day abbreviation + date number ─────────────────────────
            Column(
                modifier            = Modifier.width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text  = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = if (isSelected) 0.75f else 0.55f),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text       = date.dayOfMonth.toString(),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color      = textColor,
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(1.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )

            // ── Right: stat chips ─────────────────────────────────────────────
            if (snapshot != null) {
                WeekStatRow(snapshot = snapshot, isSelected = isSelected)
            } else if (indicator?.hasAny == true) {
                // Snapshot not yet loaded — show coloured dots as placeholder
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    if (indicator.hasSteps) DataDot(ChartColors.Movement, isSelected)
                    if (indicator.hasSleep) DataDot(ChartColors.Sleep, isSelected)
                    if (indicator.hasHR)    DataDot(ChartColors.Heart, isSelected)
                }
            } else if (!isFuture) {
                Text(
                    "No data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun WeekStatRow(
    snapshot: com.openfit.mobile.model.DailySnapshot,
    isSelected: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        // Steps
        val steps = snapshot.steps ?: 0
        if (steps > 0) {
            WeekStatChip(
                icon      = Icons.Filled.DirectionsWalk,
                color     = ChartColors.Movement,
                label     = if (steps >= 10_000) "${"%.1f".format(steps / 1000.0)}k"
                            else if (steps >= 1_000) "${steps / 1000}k" else "$steps",
                isSelected = isSelected,
            )
        }

        // Sleep
        val sleep = snapshot.sleep
        if (sleep != null && sleep.totalMinutes > 0) {
            val h = sleep.totalMinutes / 60
            val m = sleep.totalMinutes % 60
            WeekStatChip(
                icon      = Icons.Filled.Bedtime,
                color     = ChartColors.Sleep,
                label     = if (m > 0) "${h}h ${m}m" else "${h}h",
                isSelected = isSelected,
            )
        }

        // Heart rate (resting preferred, avg fallback)
        val hr = snapshot.restingHeartRateBpm ?: snapshot.heartRateAvgBpm
        if (hr != null) {
            WeekStatChip(
                icon      = Icons.Filled.Favorite,
                color     = ChartColors.Heart,
                label     = "$hr",
                isSelected = isSelected,
            )
        }
    }
}

@Composable
private fun WeekStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    label: String,
    isSelected: Boolean,
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = if (isSelected) Color.White.copy(alpha = 0.85f) else color,
            modifier           = Modifier.size(14.dp),
        )
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = if (isSelected) Color.White
                         else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Month-view individual day cells
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    indicators: DayIndicators?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> primary
            isToday    -> primary.copy(alpha = 0.12f)
            else       -> Color.Transparent
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dayCellBg",
    )
    val textColor = when {
        isSelected -> onPrimary
        isFuture   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
        isToday    -> primary
        else       -> MaterialTheme.colorScheme.onSurface
    }
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "dayCellScale",
    )

    Column(
        modifier = modifier
            .height(58.dp)
            .padding(2.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(
                if (isToday && !isSelected)
                    Modifier.border(1.5.dp, primary, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(enabled = !isFuture) { onClick() }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text       = date.dayOfMonth.toString(),
            style      = MaterialTheme.typography.labelMedium,
            color      = textColor,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            if (indicators != null && !isFuture) {
                AnimatedVisibility(
                    visible = indicators.hasSteps,
                    enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                ) { DataDot(ChartColors.Movement, isSelected) }
                AnimatedVisibility(
                    visible = indicators.hasSleep,
                    enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                ) { DataDot(ChartColors.Sleep, isSelected) }
                AnimatedVisibility(
                    visible = indicators.hasHR,
                    enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                ) { DataDot(ChartColors.Heart, isSelected) }
            }
        }
    }
}

@Composable
private fun DataDot(color: Color, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color.White.copy(alpha = 0.85f) else color)
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Shimmer skeleton cell
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ShimmerDayCell(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        initialValue  = 0.25f,
        targetValue   = 0.55f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Column(
        modifier = modifier
            .height(58.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Day detail bottom sheet
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DayDetailBottomSheet(
    date: LocalDate,
    state: DayDetailState,
    units: AppUnitSettings,
    goals: UserHealthGoals,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BottomSheetDefaults.DragHandle()

                val displayFmt = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }
                Text(
                    text       = date.format(displayFmt),
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(top = 2.dp),
                )

                val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
                val subtitle = when {
                    daysAgo == 0  -> "Today"
                    daysAgo == 1  -> "Yesterday"
                    daysAgo < 7   -> "$daysAgo days ago"
                    daysAgo < 30  -> "${daysAgo / 7} week${if (daysAgo / 7 > 1) "s" else ""} ago"
                    daysAgo < 365 -> "${daysAgo / 30} month${if (daysAgo / 30 > 1) "s" else ""} ago"
                    else          -> "${daysAgo / 365} year${if (daysAgo / 365 > 1) "s" else ""} ago"
                }
                Text(
                    text     = subtitle,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }
        },
    ) {
        when (state) {
            is DayDetailState.Loading -> DetailLoadingSkeleton()
            is DayDetailState.Loaded  -> DayDetailContent(state.snapshot, state.exercises, units, goals)
            is DayDetailState.Error   -> DetailError(state.message)
            else -> {}
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Day detail content
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DayDetailContent(
    snapshot:  DailySnapshot,
    exercises: List<ExerciseSession>,
    units:     AppUnitSettings,
    goals:     UserHealthGoals,
) {
    LazyColumn(
        modifier        = Modifier.fillMaxWidth(),
        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Steps
        if ((snapshot.steps ?: 0) > 0 || snapshot.stepsHourly.any { it > 0 }) {
            item { StepsDetailCard(snapshot, units, goals) }
        }
        // Sleep
        snapshot.sleep?.let { sleep ->
            item { SleepDetailCard(sleep, goals) }
        }
        // Heart rate
        if (snapshot.restingHeartRateBpm != null || snapshot.heartRateAvgBpm != null) {
            item { HeartRateDetailCard(snapshot) }
        }
        // Vitals
        val hasVitals = snapshot.hrvMillis != null || snapshot.spo2Percent != null ||
            snapshot.breathingRatePerMin != null || snapshot.skinTemperatureDeltaC != null
        if (hasVitals) {
            item { VitalsDetailCard(snapshot, units) }
        }
        // Activity (calories / active mins / zone mins)
        val hasActivity = snapshot.calories != null || snapshot.activeMinutes != null ||
            snapshot.zoneMinutes != null
        if (hasActivity) {
            item { ActivityDetailCard(snapshot, units, goals) }
        }
        // Workouts
        if (exercises.isNotEmpty()) {
            item { ExercisesDetailCard(exercises, units) }
        }
        // Body
        if (snapshot.weightKg != null || snapshot.bodyFatPercent != null) {
            item { BodyDetailCard(snapshot, units) }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Detail cards
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun StepsDetailCard(
    snapshot: DailySnapshot,
    units:    AppUnitSettings,
    goals:    UserHealthGoals,
) {
    val steps     = snapshot.steps ?: 0
    val stepsGoal = snapshot.stepsGoal ?: goals.stepGoal
    val progress  = (steps.toFloat() / stepsGoal.coerceAtLeast(1)).coerceIn(0f, 1f)

    DetailCard(
        icon       = Icons.Filled.DirectionsWalk,
        iconTint   = ChartColors.Movement,
        title      = "Steps",
        trailingTitle = "%,d".format(steps),
        trailingColor = ChartColors.Movement,
    ) {
        // Progress bar
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${(progress * 100).toInt()}% of daily goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Goal: %,d".format(stepsGoal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(5.dp))
            AnimatedLinearProgress(progress = progress, color = ChartColors.Movement)
        }

        // Hourly bars
        if (snapshot.stepsHourly.any { it > 0 }) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Hourly Activity")
            Spacer(Modifier.height(6.dp))
            MiniBarChart(
                data     = snapshot.stepsHourly,
                color    = ChartColors.Movement,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("12am", "6am", "12pm", "6pm", "11pm").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Sub-stats
        val hasSubStats = snapshot.distanceMeters != null || snapshot.calories != null || snapshot.floors != null
        if (hasSubStats) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                snapshot.distanceMeters?.let { MiniStat("Distance", UnitFormatter.formatDistance(it, units)) }
                snapshot.calories?.let       { MiniStat("Calories", UnitFormatter.formatCalories(it, units)) }
                snapshot.floors?.let         { MiniStat("Floors", "$it") }
            }
        }
    }
}

@Composable
private fun SleepDetailCard(sleep: SleepSession, goals: UserHealthGoals) {
    val h    = sleep.totalMinutes / 60
    val m    = sleep.totalMinutes % 60
    val goal = goals.sleepMinutesGoal
    val prog = (sleep.totalMinutes.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)

    DetailCard(
        icon       = Icons.Filled.Bedtime,
        iconTint   = ChartColors.Sleep,
        title      = "Sleep",
        subtitle   = sleep.efficiencyPercent?.let { "${it}% efficiency" },
        trailingTitle = "${h}h ${m}m",
        trailingColor = ChartColors.Sleep,
    ) {
        // Duration vs goal
        AnimatedLinearProgress(progress = prog, color = ChartColors.Sleep)
        Spacer(Modifier.height(4.dp))
        Text(
            "${(prog * 100).toInt()}% of ${goals.sleepMinutesGoal / 60}h goal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Stage breakdown
        if (sleep.stages.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Sleep Stages")
            Spacer(Modifier.height(8.dp))

            val total = sleep.stages.sumOf { it.minutes }.coerceAtLeast(1)
            // Segmented bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                sleep.stages.forEach { stage ->
                    Box(
                        modifier = Modifier
                            .weight(stage.minutes.toFloat() / total)
                            .fillMaxHeight()
                            .background(ChartColors.sleepStageColor(stage.stage))
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Stage legend
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                sleep.stages.forEach { stage ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ChartColors.sleepStageColor(stage.stage))
                            )
                            Text(
                                stage.stage.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text       = if (stage.minutes >= 60)
                                "${stage.minutes / 60}h ${stage.minutes % 60}m"
                            else "${stage.minutes}m",
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Bedtime / wake
        if (sleep.startTimeIso != null && sleep.endTimeIso != null) {
            Spacer(Modifier.height(14.dp))
            val timeFmt = remember {
                DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
            }
            val bedtime = runCatching {
                timeFmt.format(Instant.parse(sleep.startTimeIso))
            }.getOrNull()
            val wakeup = runCatching {
                timeFmt.format(Instant.parse(sleep.endTimeIso))
            }.getOrNull()
            if (bedtime != null && wakeup != null) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    MiniStat("Bedtime",  bedtime)
                    MiniStat("Wake up",  wakeup)
                }
            }
        }
    }
}

@Composable
private fun HeartRateDetailCard(snapshot: DailySnapshot) {
    val displayRhr = snapshot.restingHeartRateBpm ?: snapshot.heartRateAvgBpm
    DetailCard(
        icon          = Icons.Filled.Favorite,
        iconTint      = ChartColors.Heart,
        title         = "Heart Rate",
        trailingTitle = displayRhr?.let { "$it bpm" },
        trailingColor = ChartColors.Heart,
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            snapshot.restingHeartRateBpm?.let { HRStat("Resting", it, ChartColors.Heart) }
            snapshot.heartRateAvgBpm?.let     { HRStat("Average", it, ChartColors.Heart.copy(alpha = 0.85f)) }
            snapshot.heartRateMinBpm?.let     { HRStat("Min", it, MaterialTheme.colorScheme.onSurfaceVariant) }
            snapshot.heartRateMaxBpm?.let     { HRStat("Max", it, MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        // HR zone bar
        if (snapshot.heartRateZoneMinutes.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("HR Zones")
            Spacer(Modifier.height(8.dp))
            val zoneColors = listOf(
                Color(0xFF64B5F6), Color(0xFF81C784),
                Color(0xFFFFD54F), Color(0xFFFF8A65), ChartColors.Heart,
            )
            val total = snapshot.heartRateZoneMinutes.values.sum().coerceAtLeast(1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                snapshot.heartRateZoneMinutes.entries.forEachIndexed { i, (_, mins) ->
                    val frac = mins.toFloat() / total
                    if (frac > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(frac)
                                .fillMaxHeight()
                                .background(zoneColors.getOrElse(i) { ChartColors.Heart })
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                snapshot.heartRateZoneMinutes.entries.forEachIndexed { i, (zone, mins) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(zoneColors.getOrElse(i) { ChartColors.Heart })
                        )
                        Text("${mins}m", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalsDetailCard(snapshot: DailySnapshot, units: AppUnitSettings) {
    DetailCard(
        icon     = Icons.Filled.MonitorHeart,
        iconTint = ChartColors.Hrv,
        title    = "Vitals",
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            snapshot.hrvMillis?.let           { MiniStat("HRV",       "%.0f ms".format(it)) }
            snapshot.spo2Percent?.let         { MiniStat("SpO₂",      "%.1f%%".format(it)) }
            snapshot.breathingRatePerMin?.let { MiniStat("Breathing", "%.1f /min".format(it)) }
            snapshot.skinTemperatureDeltaC?.let {
                MiniStat("Skin Temp", UnitFormatter.formatTemperatureDelta(it, units))
            }
        }
    }
}

@Composable
private fun ActivityDetailCard(
    snapshot: DailySnapshot,
    units:    AppUnitSettings,
    goals:    UserHealthGoals,
) {
    DetailCard(
        icon     = Icons.Filled.LocalFireDepartment,
        iconTint = ChartColors.Calories,
        title    = "Activity",
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            snapshot.calories?.let       { MiniStat("Calories", UnitFormatter.formatCalories(it, units)) }
            snapshot.activeMinutes?.let  { MiniStat("Active Min", "${it}m") }
            snapshot.zoneMinutes?.let    { MiniStat("Zone Min", "$it") }
            snapshot.sedentarySeconds?.let {
                MiniStat("Sedentary", "${it / 3600}h ${(it % 3600) / 60}m")
            }
        }

        snapshot.activeMinutes?.let { active ->
            Spacer(Modifier.height(12.dp))
            val prog = (active.toFloat() / goals.activeMinutesGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Active minutes", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Goal: ${goals.activeMinutesGoal}m", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            AnimatedLinearProgress(progress = prog, color = ChartColors.Cardio)
        }
    }
}

@Composable
private fun ExercisesDetailCard(exercises: List<ExerciseSession>, units: AppUnitSettings) {
    DetailCard(
        icon     = Icons.Filled.FitnessCenter,
        iconTint = ChartColors.Cardio,
        title    = "Workouts",
        subtitle = "${exercises.size} session${if (exercises.size != 1) "s" else ""}",
    ) {
        exercises.forEachIndexed { idx, ex ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = ex.originalType
                            .replace('_', ' ')
                            .split(' ')
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val durationStr = "${ex.durationMinutes} min"
                    val distStr = ex.distanceMeters?.let { " · ${UnitFormatter.formatDistance(it, units)}" } ?: ""
                    Text(
                        text  = durationStr + distStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    ex.averageHeartRateBpm?.let { hr ->
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint     = ChartColors.Heart,
                                modifier = Modifier.size(12.dp),
                            )
                            Text("$hr", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ex.caloriesBurned?.let {
                        Text(
                            UnitFormatter.formatCalories(it, units),
                            style = MaterialTheme.typography.bodySmall,
                            color = ChartColors.Calories,
                        )
                    }
                }
            }
            if (idx < exercises.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun BodyDetailCard(snapshot: DailySnapshot, units: AppUnitSettings) {
    DetailCard(
        icon     = Icons.Filled.MonitorWeight,
        iconTint = ChartColors.Body,
        title    = "Body",
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            snapshot.weightKg?.let       { MiniStat("Weight",   UnitFormatter.formatWeight(it, units)) }
            snapshot.bodyFatPercent?.let { MiniStat("Body Fat", "%.1f%%".format(it)) }
            snapshot.waterLiters?.let    { MiniStat("Water",    UnitFormatter.formatWater(it, units)) }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Reusable detail card shell
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetailCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    trailingTitle: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier            = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.14f)),
                        contentAlignment    = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = icon,
                            contentDescription = null,
                            tint               = iconTint,
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text       = title,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (subtitle != null) {
                            Text(
                                text  = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (trailingTitle != null) {
                    Text(
                        text       = trailingTitle,
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color      = trailingColor,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Loading skeleton & error
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetailLoadingSkeleton() {
    val inf = rememberInfiniteTransition(label = "skeleton")
    val alpha by inf.animateFloat(
        initialValue  = 0.25f,
        targetValue   = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label         = "skeletonAlpha",
    )
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { idx ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (idx == 0) 140.dp else 110.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DetailError(message: String) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.error,
                modifier           = Modifier.size(40.dp),
            )
            Text(
                text  = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Small reusable components
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HRStat(label: String, bpm: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text       = "$bpm",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = color,
            )
            Text(
                text     = " bpm",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Animated linear progress bar that springs from 0 → value on first composition. */
@Composable
private fun AnimatedLinearProgress(progress: Float, color: Color) {
    var trigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { trigger = true }
    val anim by animateFloatAsState(
        targetValue   = if (trigger) progress else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "progress",
    )
    LinearProgressIndicator(
        progress    = { anim },
        modifier    = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color       = color,
        trackColor  = color.copy(alpha = 0.14f),
    )
}

/** Compact 24-bucket hourly bar chart drawn on Canvas. */
@Composable
private fun MiniBarChart(
    data:     List<Int>,
    color:    Color,
    modifier: Modifier = Modifier,
) {
    val maxVal = (data.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
    val bars   = data.take(24).map { it.toFloat() }

    Canvas(modifier = modifier) {
        val bw      = size.width / 24f
        val spacing = bw * 0.18f
        val abw     = bw - spacing

        bars.forEachIndexed { i, v ->
            val barH = (v / maxVal) * size.height
            drawRoundRect(
                color       = color.copy(alpha = if (v > 0f) 0.85f else 0.1f),
                topLeft     = Offset(i * bw + spacing / 2f, size.height - barH),
                size        = Size(abw, barH.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }
    }
}
