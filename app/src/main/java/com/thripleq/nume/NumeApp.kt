package com.thripleq.nume

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thripleq.nume.ui.screens.LibraryScreen
import com.thripleq.nume.ui.screens.PlayerScreen

object Routes {
    const val PLAYER = "player"
    const val HOME = "home"
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
            LibraryScreen(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
        }
        composable(Routes.PLAYER) { PlayerScreen() }
    }
}