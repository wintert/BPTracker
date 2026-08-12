package com.talwinter.bptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.talwinter.bptracker.ui.BpViewModel
import com.talwinter.bptracker.ui.screens.AddReadingScreen
import com.talwinter.bptracker.ui.screens.HistoryScreen
import com.talwinter.bptracker.ui.screens.HomeScreen
import com.talwinter.bptracker.ui.screens.OnboardingScreen
import com.talwinter.bptracker.ui.screens.SettingsScreen
import com.talwinter.bptracker.ui.theme.BpTrackerTheme
import com.talwinter.bptracker.ui.theme.ScaledText

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ADD = "add"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDIT = "edit/{readingId}"
    fun edit(id: Long) = "edit/$id"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BpTrackerTheme {
                val vm: BpViewModel = viewModel()
                val state by vm.state.collectAsState()

                // NavHost fixes its start destination on first composition, so wait for
                // DataStore to answer rather than guessing and stranding onboarding.
                val onboarded = state.hasOnboarded ?: return@BpTrackerTheme

                ScaledText(state.textScale) {
                val nav = rememberNavController()

                NavHost(
                    navController = nav,
                    startDestination = if (onboarded) Routes.HOME else Routes.ONBOARDING
                ) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(
                            onDone = { guideline ->
                                vm.setGuideline(guideline)
                                vm.completeOnboarding()
                                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                            }
                        )
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            state = state,
                            onAdd = { nav.navigate(Routes.ADD) },
                            onHistory = { nav.navigate(Routes.HISTORY) },
                            onSettings = { nav.navigate(Routes.SETTINGS) }
                        )
                    }
                    composable(Routes.ADD) {
                        AddReadingScreen(vm = vm, state = state, readingId = null, onDone = { nav.popBackStack() })
                    }
                    composable(Routes.EDIT) { entry ->
                        val id = entry.arguments?.getString("readingId")?.toLongOrNull()
                        AddReadingScreen(vm = vm, state = state, readingId = id, onDone = { nav.popBackStack() })
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            state = state,
                            onEdit = { nav.navigate(Routes.edit(it)) },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(vm = vm, state = state, onBack = { nav.popBackStack() })
                    }
                }
                }
            }
        }
    }
}
