package com.thripleq.nume

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.thripleq.nume.ui.screens.ChartDetailScreen
import com.thripleq.nume.ui.screens.HomeScreen
import com.thripleq.nume.ui.screens.LibraryScreen
import com.thripleq.nume.ui.screens.PlayerScreen
import com.thripleq.nume.ui.screens.ProfileScreen
import com.thripleq.nume.ui.screens.SearchScreen
import com.thripleq.nume.ui.screens.TrackListScreen
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. Navigation lives only in [NumeApp]. */
@Serializable
object Home

@Serializable
object Library

@Serializable
object Search

@Serializable
object Profile

@Serializable
object Player

/** [ChartDetailScreen] parameters, carried as structured args. */
@Serializable
data class ChartDestination(val chartId: String, val name: String)

/**
 * Generic track list opened from the Profile tab: liked tracks, purchases, a
 * playlist or an album (see [TrackListSource] for the `source` values).
 */
@Serializable
data class TrackListDestination(val source: String, val id: String, val title: String)

/** The top-level tabs shown in the floating capsule. */
private enum class BottomTab(
    val route: Any,
    val label: String,
    val icon: ImageVector,
) {
    ExploreTab(route = Home, label = "探索", icon = Icons.Filled.Home),
    SearchTab(route = Search, label = "搜索", icon = Icons.Filled.Search),
    ProfileTab(route = Profile, label = "我的", icon = Icons.Filled.Person),
}

/** Root of the Compose UI: navigation graph + floating capsule. */
@Composable
fun NumeApp() {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val selectedTab = when {
        destination?.hasRoute<Home>() == true -> BottomTab.ExploreTab
        destination?.hasRoute<Search>() == true -> BottomTab.SearchTab
        destination?.hasRoute<Profile>() == true -> BottomTab.ProfileTab
        else -> null // detail screens (chart / player) hide the capsule
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<Home> { HomeScreen() }
            composable<Library> {
                LibraryScreen(
                    onOpenChart = { id, name ->
                        navController.navigate(ChartDestination(chartId = id, name = name))
                    },
                )
            }
            composable<ChartDestination> { entry ->
                val args = entry.toRoute<ChartDestination>()
                ChartDetailScreen(
                    chartId = args.chartId,
                    name = args.name,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Player) },
                )
            }
            composable<Search> { SearchScreen() }
            composable<Profile> {
                ProfileScreen(
                    onOpenTracks = { source, id, title ->
                        navController.navigate(TrackListDestination(source, id, title))
                    },
                )
            }
            composable<TrackListDestination> { entry ->
                val args = entry.toRoute<TrackListDestination>()
                TrackListScreen(
                    source = args.source,
                    id = args.id,
                    title = args.title,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Player) },
                )
            }
            composable<Player> { PlayerScreen() }
        }

        if (selectedTab != null) {
            CapsuleBar(
                selected = selectedTab,
                onSelect = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(vertical = 14.dp),
            )
        }
    }
}

/**
 * Floating capsule: keeps the rounded-pill island shape from the glass version,
 * but rendered with plain Material colors (no backdrop blur). Width is 7/8 of
 * the screen; tabs are evenly split, with the selected one on a theme-color pill.
 */
@Composable
private fun CapsuleBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(percent = 50)
    val capsuleHeight = 56.dp
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val islandWidth = screenWidth * 7f / 8f

    Row(
        modifier = modifier
            .width(islandWidth)
            .height(capsuleHeight)
            .shadow(elevation = 12.dp, shape = pill, clip = false)
            .clip(pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = pill)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pill)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
