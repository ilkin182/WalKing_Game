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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.BuildConfig
import com.example.domain.model.User
import com.example.ui.util.LocalWindowWidthSizeClass

// Dev-only test account; only ever read from inside `if (BuildConfig.DEBUG)`. Create this user
// manually in Firebase Console (Authentication > Users > Add user) — see the project notes.
private const val DEV_TEST_EMAIL = "test@example.com"
private const val DEV_TEST_PASSWORD = "Test1234!"

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: (User) -> Unit,
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val passwordResetEmailSent by viewModel.passwordResetEmailSent.collectAsStateWithLifecycle()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AuthUiState.Success) {
            onLoginSuccess(state.user)
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
                text = "STOMPED",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("login_title")
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
                    .testTag("login_email_field")
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
                    .testTag("login_password_field")
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showForgotPasswordDialog = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("login_forgot_password_link")
            ) {
                Text("Forgot password?", color = Color(0xFF5DF2D6))
            }

            val errorMessage = localError ?: (uiState as? AuthUiState.Error)?.message
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 4.dp)
                        .testTag("login_error_text")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isLoading = uiState is AuthUiState.Loading
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        localError = "Email and password are required."
                    } else {
                        viewModel.login(email, password)
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
                    .testTag("login_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color(0xFF0A1F1C))
                } else {
                    Text("LOG IN", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onNavigateToSignUp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("login_signup_link")
            ) {
                Text("Don't have an account? Sign up", color = Color(0xFF98BCB6))
            }

            // Dev-only convenience: fills and submits a fixed test account so you don't have to
            // retype credentials on every run. BuildConfig.DEBUG is a compile-time constant, so
            // R8 dead-code-eliminates this whole block (and the button) out of release builds.
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        email = DEV_TEST_EMAIL
                        password = DEV_TEST_PASSWORD
                        viewModel.login(DEV_TEST_EMAIL, DEV_TEST_PASSWORD)
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dev_quick_login_button")
                ) {
                    Text("QUICK LOGIN (DEV ONLY)", color = Color(0xFFF9A825))
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            passwordResetEmailSent = passwordResetEmailSent,
            onSendReset = { resetEmail -> viewModel.sendPasswordReset(resetEmail) },
            onDismiss = { showForgotPasswordDialog = false }
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    passwordResetEmailSent: Boolean,
    onSendReset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var resetEmail by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF142021),
        title = { Text("Reset Password", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (passwordResetEmailSent) {
                    Text(
                        text = "If an account exists for that email, a reset link has been sent.",
                        color = Color(0xFF5DF2D6),
                        modifier = Modifier.testTag("forgot_password_sent_message")
                    )
                } else {
                    Text(
                        text = "Enter your account email and we'll send you a reset link.",
                        color = Color(0xFF98BCB6),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = authTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email_field")
                    )
                }
            }
        },
        confirmButton = {
            if (!passwordResetEmailSent) {
                TextButton(
                    onClick = { onSendReset(resetEmail) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF5DF2D6)),
                    modifier = Modifier.testTag("forgot_password_send_button")
                ) {
                    Text("SEND")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("DONE", color = Color(0xFF5DF2D6))
                }
            }
        },
        dismissButton = {
            if (!passwordResetEmailSent) {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = Color.White)
                }
            }
        }
    )
}

@Composable
internal fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF5DF2D6),
    unfocusedBorderColor = Color(0xFF26524D),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFF5DF2D6),
    unfocusedLabelColor = Color(0xFF98BCB6),
    cursorColor = Color(0xFF5DF2D6)
)
