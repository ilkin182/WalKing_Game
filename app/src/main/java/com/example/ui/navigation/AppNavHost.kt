package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.SignUpScreen
import com.example.ui.legal.PrivacyPolicyScreen
import com.example.ui.map.GameViewModel
import com.example.ui.map.MapScreen

@Composable
fun AppNavHost(
    authViewModel: AuthViewModel,
    gameViewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val startDestination = if (authViewModel.currentUser() != null) Routes.MAP else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    authViewModel.consumeError()
                    navController.navigate(Routes.SIGN_UP)
                }
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                viewModel = authViewModel,
                onSignUpSuccess = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    authViewModel.consumeError()
                    navController.popBackStack()
                },
                onOpenPrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) }
            )
        }
        composable(Routes.MAP) {
            MapScreen(
                viewModel = gameViewModel,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAP) { inclusive = true }
                    }
                },
                onOpenPrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) }
            )
        }
        composable(Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onClose = { navController.popBackStack() })
        }
    }
}
