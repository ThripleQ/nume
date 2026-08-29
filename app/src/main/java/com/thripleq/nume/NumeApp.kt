package com.thripleq.nume

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.thripleq.nume.ui.screens.ChartDetailScreen
import com.thripleq.nume.ui.screens.LibraryScreen
import com.thripleq.nume.ui.screens.PlayerScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations, mirroring the screen set. Navigation
 * concerns live only here: screens stay navigation-agnostic and are given
 * plain callbacks by [NumeApp].
 */
@Serializable
object Home

@Serializable
object Player

/** [ChartDetailScreen] parameters, carried as structured args. */
@Serializable
data class ChartDestination(val chartId: String, val name: String)

/** Root of the Compose UI. Navigation lives here; screens below stay navigation-agnostic. */
@Composable
fun NumeApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = Modifier,
    ) {
        composable<Home> {
            LibraryScreen(
                onOpenChart = { id, name ->
                    navController.navigate(ChartDestination(chartId = id, name = name))
                },
            )
        }
        composable<ChartDestination> { backStackEntry ->
            // chartId/name serialized by the type-safe route; read through to the screen.
            val args = backStackEntry.toRoute<ChartDestination>()
            ChartDetailScreen(
                chartId = args.chartId,
                name = args.name,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Player) },
            )
        }
        composable<Player> { PlayerScreen() }
    }
}