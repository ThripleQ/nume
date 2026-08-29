package com.thripleq.nume

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thripleq.nume.ui.screens.ChartDetailScreen
import com.thripleq.nume.ui.screens.LibraryScreen
import com.thripleq.nume.ui.screens.PlayerScreen

object Routes {
    const val PLAYER = "player"
    const val HOME = "home"
    const val CHART = "chart/{chartId}/{name}"
    fun chart(id: String, name: String) = "chart/$id/${Uri.encode(name)}"
}

/** Root of the Compose UI. Navigation lives here; screens below stay navigation-agnostic. */
@Composable
fun NumeApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier,
    ) {
        composable(Routes.HOME) {
            LibraryScreen(
                onOpenChart = { id, name -> navController.navigate(Routes.chart(id, name)) },
            )
        }
        composable(
            route = Routes.CHART,
            arguments = listOf(
                navArgument("chartId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            ChartDetailScreen(
                chartId = backStackEntry.arguments?.getString("chartId").orEmpty(),
                name = backStackEntry.arguments?.getString("name").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
            )
        }
        composable(Routes.PLAYER) { PlayerScreen() }
    }
}