package sk.matej.spajza.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import sk.matej.spajza.ui.cook.CookRoute
import sk.matej.spajza.ui.cooking.CookingModeRoute
import sk.matej.spajza.ui.pantry.PantryRoute
import sk.matej.spajza.ui.profile.AboutScreen
import sk.matej.spajza.ui.profile.ProfileRoute
import sk.matej.spajza.ui.recipe.RecipeDetailRoute
import sk.matej.spajza.ui.shopping.ShoppingRoute

@Composable
fun MainScaffold(link: AppLink?, onLinkHandled: (AppLink) -> Unit) {
    val navController = rememberNavController()
    LaunchedEffect(link) {
        val target = link ?: return@LaunchedEffect
        when (target) {
            AppLink.Pantry -> navController.navigateToTab(PantryDestination)
        }
        onLinkHandled(target)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination
    val onTopLevel = TopLevelDestination.entries.any { current.isOn(it) }
    Scaffold(
        // Each screen handles the status bar itself; the bottom bar handles the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current.isOn(tab),
                            onClick = { navController.navigateToTab(tab.destination) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CookDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<CookDestination> {
                CookRoute(
                    onOpenRecipe = { navController.navigate(RecipeDestination(it)) },
                    onOpenPantry = { navController.navigateToTab(PantryDestination) },
                )
            }
            composable<PantryDestination> { PantryRoute() }
            composable<ShoppingDestination> { ShoppingRoute() }
            composable<ProfileDestination> { ProfileRoute(onOpenAbout = { navController.navigate(AboutDestination) }) }
            composable<RecipeDestination> {
                RecipeDetailRoute(
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { navController.navigate(RecipeDestination(it)) },
                    onStartCooking = { recipeId, servings -> navController.navigate(CookingDestination(recipeId, servings)) },
                )
            }
            composable<CookingDestination> { CookingModeRoute(onExit = { navController.popBackStack() }) }
            composable<AboutDestination> { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavDestination?.isOn(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true

private fun NavHostController.navigateToTab(destination: Any) {
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
