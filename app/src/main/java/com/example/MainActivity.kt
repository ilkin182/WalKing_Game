package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.di.AppContainer
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.AuthViewModelFactory
import com.example.ui.map.GameViewModel
import com.example.ui.map.GameViewModelFactory
import com.example.ui.navigation.AppNavHost
import com.example.ui.race.RaceViewModel
import com.example.ui.race.RaceViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.LocalWindowWidthSizeClass

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(appContainer.authUseCases)
    }
    private val gameViewModel: GameViewModel by viewModels {
        GameViewModelFactory(appContainer.gameUseCases)
    }
    private val raceViewModel: RaceViewModel by viewModels {
        RaceViewModelFactory(appContainer.raceUseCases)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge full screen drawing
        enableEdgeToEdge()

        // A race invitation the app was launched by. Handed to the ViewModel rather than acted on:
        // it only puts the invitation up for a yes or no, and it survives the login screen if the
        // player is not signed in yet, because the ViewModel outlives the navigation.
        handleRaceInvite(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass) {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.ui.graphics.Color(0xFF0F1A1B) // Deep dark obsidian backdrop
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavHost(
                                authViewModel = authViewModel,
                                gameViewModel = gameViewModel,
                                raceViewModel = raceViewModel
                            )
                            // Temporary: forces a crash so Crashlytics receives its first report.
                            // Debug builds only; remove once the crash shows up in the Firebase console.
                            if (BuildConfig.DEBUG) {
                                Button(
                                    onClick = { throw RuntimeException("Test Crash") },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                ) {
                                    Text("Test Crash")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A second invitation arriving while the app is already open.
     *
     * The activity is `singleTask` (see the manifest) precisely so this happens instead of a second
     * copy of the app being stacked on the first - a player who taps two invitations should end up
     * with two offers in one app, not two maps.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRaceInvite(intent)
    }

    private fun handleRaceInvite(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = intent.dataString ?: return
        raceViewModel.onInviteLink(link)
    }
}
