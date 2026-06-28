package com.hypno.hypnovibe.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hypno.hypnovibe.ui.theme.*

@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = DarkStoneBrown,
        contentColor = SilverGray
    ) {
        val tabs = listOf(Screen.Home, Screen.Playlist, Screen.Editor, Screen.Device)
        tabs.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.label,
                        tint = if (selected) BloodRed else DarkGray
                    )
                },
                label = {
                    Text(
                        text = screen.label,
                        color = if (selected) GoldAncient else DarkGray
                    )
                },
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = BloodRed.copy(alpha = 0.2f)
                )
            )
        }
    }
}
