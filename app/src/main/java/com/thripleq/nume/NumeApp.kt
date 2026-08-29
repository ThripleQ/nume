package com.thripleq.nume

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.Track
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
    val context = LocalContext.current.applicationContext

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier,
    ) {
        composable(Routes.HOME) {
            LibraryScreen(
                onPlayTrack = { track: Track ->
                    PlaybackLauncher.play(context, track)
                    navController.navigate(Routes.PLAYER)
                },
            )
        }
        composable(Routes.PLAYER) { PlayerScreen() }
    }
}