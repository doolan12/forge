package com.doolan.forge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.doolan.forge.ui.AppViewModel
import com.doolan.forge.ui.Accent
import com.doolan.forge.ui.Bg
import com.doolan.forge.ui.CalendarScreen
import com.doolan.forge.ui.ForgeTheme
import com.doolan.forge.ui.HomeScreen
import com.doolan.forge.ui.Muted
import com.doolan.forge.ui.OnboardingScreen
import com.doolan.forge.ui.ProfileScreen
import com.doolan.forge.ui.ProgressScreen
import com.doolan.forge.ui.SurfaceDark
import com.doolan.forge.ui.TrainingScreen
import com.doolan.forge.ui.WorkoutScreen
import com.doolan.forge.ui.WorkoutViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    ForgeRoot()
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("training", "Training", Icons.Default.FitnessCenter),
    Tab("calendar", "Calendar", Icons.Default.CalendarMonth),
    Tab("progress", "Progress", Icons.Default.BarChart),
    Tab("profile", "Profile", Icons.Default.Person)
)

@Composable
private fun ForgeRoot() {
    val app = LocalContext.current.applicationContext as ForgeApp
    val vm: AppViewModel = viewModel(factory = viewModelFactory {
        initializer { AppViewModel(app.repo) }
    })
    val profile by vm.profile.collectAsState()
    val p = profile

    when {
        p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", color = Muted)
        }
        !p.onboarded -> OnboardingScreen(vm)
        else -> MainNav(vm, app)
    }
}

@Composable
private fun MainNav(vm: AppViewModel, app: ForgeApp) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TABS.any { it.route == route }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = SurfaceDark) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label, maxLines = 1, softWrap = false, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Bg,
                                selectedTextColor = Accent,
                                indicatorColor = Accent,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(inner)) {
            composable("home") { HomeScreen(vm) { id -> nav.navigate("workout/$id") } }
            composable("training") { TrainingScreen(vm) { id -> nav.navigate("workout/$id") } }
            composable("calendar") { CalendarScreen(vm) { id -> nav.navigate("workout/$id") } }
            composable("progress") { ProgressScreen(vm) }
            composable("profile") { ProfileScreen(vm) }
            composable(
                "workout/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("id") ?: 0L
                val wvm: WorkoutViewModel = viewModel(
                    key = "workout_$id",
                    factory = viewModelFactory { initializer { WorkoutViewModel(app.repo, id) } }
                )
                WorkoutScreen(wvm, vm) { nav.popBackStack() }
            }
        }
    }
}
