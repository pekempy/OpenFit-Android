package com.openfit.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations.
 *
 * Bottom bar (primary): Today · Sleep · Activity · Coach · You
 *
 * Secondary (pushed from YouScreen, no bottom bar):
 *   Vitals · Body · History · Devices · Settings
 */
sealed class Destination(val route: String, val label: String, val icon: ImageVector) {

    // ── Primary bottom-nav tabs ───────────────────────────────────────────
    data object Today    : Destination("today",    "Today",    Icons.Filled.Home)
    data object Sleep    : Destination("sleep",    "Sleep",    Icons.Filled.Bedtime)
    data object Activity : Destination("activity", "Activity", Icons.Filled.DirectionsRun)
    data object Coach    : Destination("coach",    "Coach",    Icons.Filled.Forum)
    data object You      : Destination("you",      "You",      Icons.Filled.Person)

    // ── Secondary destinations (navigated from YouScreen) ─────────────────
    data object Vitals   : Destination("vitals",   "Vitals",   Icons.Filled.Favorite)
    data object Body     : Destination("body",     "Body",     Icons.Filled.MonitorWeight)
    data object History  : Destination("history",  "History",  Icons.Filled.CalendarMonth)
    data object Devices  : Destination("devices",  "Devices",  Icons.Filled.Watch)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val bottomBarItems: List<Destination>
            get() = listOf(Today, Sleep, Activity, Coach, You)

        /** Routes that belong to the secondary (pushed) back-stack; the bottom
         *  bar and FAB are hidden while any of these is current. */
        val secondaryRoutes: Set<String>
            get() = setOf(
                Vitals.route, Body.route, History.route, Devices.route, Settings.route,
            )
    }
}

const val ROUTE_ONBOARDING = "onboarding"
