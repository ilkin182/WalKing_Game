package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Countries
import com.example.domain.model.User
import com.example.ui.components.CountrySelectorField
import com.example.ui.util.LocalWindowWidthSizeClass

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onSignUpSuccess: (User) -> Unit,
    onNavigateToLogin: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    // Only the code survives a rotation - the display name is looked up from it, so there is no way
    // for the two to drift apart.
    var countryCode by rememberSaveable { mutableStateOf(Countries.deviceDefault()?.code) }
    var localError by remember { mutableStateOf<String?>(null) }

    val country = Countries.byCode(countryCode)

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AuthUiState.Success) {
            onSignUpSuccess(state.user)
        }
    }

    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val isCompactWidth = windowWidthSizeClass == WindowWidthSizeClass.Compact

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F1A1B), Color(0xFF142D2D), Color(0xFF0C1414))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (isCompactWidth) Modifier.fillMaxWidth() else Modifier.width(480.dp))
                .align(if (isCompactWidth) Alignment.TopStart else Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CREATE ACCOUNT",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("signup_title")
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    localError = null
                },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = authTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_email_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Asked for at sign-up because it is what the country leaderboard groups players by,
            // and pre-filled from the device region so most players only have to confirm it.
            CountrySelectorField(
                selected = country,
                onSelect = {
                    countryCode = it.code
                    localError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_country_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    localError = null
                },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = authTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_password_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    localError = null
                },
                label = { Text("Confirm Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = authTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_confirm_password_field")
            )

            val errorMessage = localError ?: (uiState as? AuthUiState.Error)?.message
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("signup_error_text")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isLoading = uiState is AuthUiState.Loading
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                        localError = "All fields are required."
                    } else if (country == null) {
                        localError = "Please select your country."
                    } else {
                        viewModel.signUp(email, password, confirmPassword, country.code)
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5DF2D6),
                    contentColor = Color(0xFF0A1F1C)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("signup_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color(0xFF0A1F1C))
                } else {
                    Text("SIGN UP", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onNavigateToLogin,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("signup_login_link")
            ) {
                Text("Already have an account? Log in", color = Color(0xFF98BCB6))
            }

            TextButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("signup_privacy_policy_link")
            ) {
                Text("By signing up you agree to our Privacy Policy", color = Color(0xFF5A7C77), fontSize = 12.sp)
            }
        }
    }
}
