package com.openfit.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Watch
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level destinations shown in the bottom navigation bar.
 * Streamlined 5-tab navigation: Today, Metrics (Activity/Sleep/Vitals/Body), Devices, Coach, Settings. */
sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Today    : Destination("today",    "Today",    Icons.Filled.Today)
    data object Calendar : Destination("calendar", "History",  Icons.Filled.CalendarMonth)
    data object Metrics  : Destination("metrics",  "Metrics",  Icons.Filled.Insights)
    data object Devices  : Destination("devices",  "Devices",  Icons.Filled.Watch)
    data object Coach    : Destination("coach",    "Coach",    Icons.Filled.Forum)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)

    // Sub-routes kept for backward compatibility and deep links
    data object Activity : Destination("activity", "Activity", Icons.Filled.DirectionsWalk)
    data object Sleep : Destination("sleep", "Sleep", Icons.Filled.Bedtime)
    data object Health : Destination("health", "Health", Icons.Filled.Favorite)
    data object Body : Destination("body", "Body", Icons.Filled.MonitorWeight)
    data object Data : Destination("devices", "Devices", Icons.Filled.Watch)

    companion object {
        val bottomBarItems: List<Destination>
            get() = listOf(Today, Metrics, Devices, Coach, Settings)
    }
}

const val ROUTE_ONBOARDING = "onboarding"
