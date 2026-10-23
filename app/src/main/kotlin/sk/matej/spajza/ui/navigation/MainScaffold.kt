package sk.matej.spajza.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import sk.matej.spajza.domain.repository.AuthUser
import sk.matej.spajza.ui.cook.CookRoute
import sk.matej.spajza.ui.pantry.PantryScreen
import sk.matej.spajza.ui.profile.ProfileRoute
import sk.matej.spajza.ui.shopping.ShoppingScreen

@Composable
fun MainScaffold(user: AuthUser) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination
    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true,
                        onClick = {
                            navController.navigate(tab.destination) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = CookDestination, modifier = Modifier.padding(padding)) {
            composable<CookDestination> { CookRoute() }
            composable<PantryDestination> { PantryScreen() }
            composable<ShoppingDestination> { ShoppingScreen() }
            composable<ProfileDestination> { ProfileRoute(user) }
        }
    }
}
