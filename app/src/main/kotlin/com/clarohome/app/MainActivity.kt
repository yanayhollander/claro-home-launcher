package com.clarohome.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clarohome.core.design.ClaroTheme
import com.clarohome.core.domain.StartupRecoveryState
import com.clarohome.core.model.VisualMode
import com.clarohome.presentation.classic.ClassicPhoneScreen
import com.clarohome.presentation.touch.TouchHomeScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable
private data object RecoveryRoute

@Serializable
private data object TouchHomeRoute

@Serializable
private data object ClassicHomeRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaroTheme {
                ClaroApp()
            }
        }
    }
}

@Composable
private fun ClaroApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    LaunchedEffect(state.startup, state.visualMode) {
        if (state.startup is StartupRecoveryState.Ready) {
            navController.navigate(state.visualMode.route()) {
                popUpTo(RecoveryRoute) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = RecoveryRoute,
    ) {
        composable<RecoveryRoute> {
            when (val startup = state.startup) {
                StartupRecoveryState.Recovering -> LoadingScreen()
                is StartupRecoveryState.Failed -> FailureScreen(startup.reason)
                is StartupRecoveryState.Ready -> LoadingScreen()
            }
        }
        composable<TouchHomeRoute> {
            state.home?.let { home ->
                TouchHomeScreen(
                    home = home,
                    onUseClassicMode = { viewModel.setVisualMode(VisualMode.CLASSIC) },
                )
            }
        }
        composable<ClassicHomeRoute> {
            state.home?.let { home ->
                ClassicPhoneScreen(
                    home = home,
                    onUseTouchMode = { viewModel.setVisualMode(VisualMode.TOUCH) },
                )
            }
        }
    }
}

private fun VisualMode.route(): Any = when (this) {
    VisualMode.TOUCH -> TouchHomeRoute
    VisualMode.CLASSIC -> ClassicHomeRoute
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FailureScreen(reason: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(reason)
    }
}
