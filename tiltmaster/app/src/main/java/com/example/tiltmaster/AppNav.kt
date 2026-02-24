package com.example.tiltmaster

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.tiltmaster.ui.*

sealed class Screen(val route: String) {
    data object Menu : Screen("menu")
    data object LevelSelect : Screen("level_select")
    data object Settings : Screen("settings")
    data object Game : Screen("game/{levelId}") {
        fun createRoute(levelId: Int) = "game/$levelId"
    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val settingsVM: SettingsViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Menu.route
    ) {
        composable(Screen.Menu.route) {
            MenuScreen(
                onPlay = { navController.navigate(Screen.LevelSelect.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.LevelSelect.route) {
            LevelSelectScreen(
                onBack = { navController.popBackStack() },
                onSelectLevel = { levelId ->
                    navController.navigate(Screen.Game.createRoute(levelId))
                }
            )
        }

        composable(
            route = Screen.Game.route,
            arguments = listOf(navArgument("levelId") { type = NavType.IntType })
        ) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
            GameScreen(
                levelId = levelId,
                onExit = { navController.popBackStack() },
                onGoLevelSelect = {
                    navController.navigate(Screen.LevelSelect.route) {
                        popUpTo(Screen.Menu.route) { inclusive = false }
                    }
                },
                onGoMainMenu = {
                    navController.navigate(Screen.Menu.route) {
                        popUpTo(Screen.Menu.route) { inclusive = true }
                    }
                },
                onGoSettings = { navController.navigate(Screen.Settings.route) },
                settingsVM = settingsVM
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                vm = settingsVM
            )
        }
    }
}