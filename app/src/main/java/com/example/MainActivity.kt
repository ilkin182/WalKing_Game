package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.example.di.AppContainer
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.AuthViewModelFactory
import com.example.ui.map.GameViewModel
import com.example.ui.map.GameViewModelFactory
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.LocalWindowWidthSizeClass

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge full screen drawing
        enableEdgeToEdge()

        // Wire up the dependency graph (manual DI, no Hilt in this project)
        val appContainer = AppContainer(applicationContext)

        val authViewModel: AuthViewModel by viewModels {
            AuthViewModelFactory(appContainer.authUseCases)
        }
        val gameViewModel: GameViewModel by viewModels {
            GameViewModelFactory(appContainer.gameUseCases)
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass) {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.ui.graphics.Color(0xFF0F1A1B) // Deep dark obsidian backdrop
                    ) {
                        AppNavHost(authViewModel = authViewModel, gameViewModel = gameViewModel)
                    }
                }
            }
        }
    }
}
