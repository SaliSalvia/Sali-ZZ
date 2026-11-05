package com.salizzai.app.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class AuthMode { Login, Signup, Otp }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthed: () -> Unit,
    onContinueAsGuest: () -> Unit,
    googleWebClientId: String,
    activity: android.app.Activity?
) {
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.provideFactory())
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pendingOtpEmail by viewModel.pendingOtpEmail.collectAsState()
    val lastIssuedCode by viewModel.lastIssuedCode.collectAsState()

    var mode by rememberSaveable { mutableStateOf(AuthMode.Login) }
    val keyboard = LocalSoftwareKeyboardController.current

    // Auto-transition when authed
    if (state is com.salizzai.app.data.model.AuthState.LoggedIn) {
        onAuthed()
    }

    // OTP mode is driven by pendingOtpEmail from the ViewModel
    val effectiveMode = if (pendingOtpEmail != null) AuthMode.Otp else mode

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Z",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Sali-ZZai",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "دستیار هوشمند Z.ai برای اندروید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Animated mode switching
            androidx.compose.animation.Crossfade(
                targetState = effectiveMode,
                label = "auth-mode"
            ) { currentMode ->
                when (currentMode) {
                    AuthMode.Login -> LoginForm(
                        isLoading = isLoading,
                        onSubmit = { email, password ->
                            keyboard?.hide()
                            viewModel.signInWithEmailPassword(email, password)
                        },
                        onForgotPassword = { mode = AuthMode.Otp; viewModel.cancelOtp() },
                        onSwitchToOtp = { email ->
                            keyboard?.hide()
                            viewModel.startOtpLogin(email)
                        }
                    )
                    AuthMode.Signup -> SignupForm(
                        isLoading = isLoading,
                        onSubmit = { email, password, name, username ->
                            keyboard?.hide()
                            viewModel.signUpWithEmail(email, password, name, username)
                        }
                    )
                    AuthMode.Otp -> OtpForm(
                        email = pendingOtpEmail ?: "",
                        issuedCode = lastIssuedCode?.second,
                        isLoading = isLoading,
                        onSubmit = { code ->
                            keyboard?.hide()
                            viewModel.verifyOtp(code)
                        },
                        onResend = { viewModel.startOtpLogin(pendingOtpEmail ?: "") },
                        onBack = {
                            viewModel.cancelOtp()
                            mode = AuthMode.Login
                        }
                    )
                }
            }

            // Error banner
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
            ) {
                error?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(20.dp)) {
                                Text("✕", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mode switcher
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                when (effectiveMode) {
                    AuthMode.Login -> TextButton(onClick = { mode = AuthMode.Signup; viewModel.cancelOtp() }) {
                        Text("حساب ندارید؟ ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "ثبت‌نام کنید",
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                    AuthMode.Signup -> TextButton(onClick = { mode = AuthMode.Login; viewModel.cancelOtp() }) {
                        Text("حساب دارید؟ ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "وارد شوید",
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                    AuthMode.Otp -> { /* Back button is inside OtpForm */ }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Google sign-in
            GoogleSignInButton(
                onClick = {
                    keyboard?.hide()
                    if (activity != null) {
                        viewModel.signInWithGoogle(googleWebClientId, activity)
                    }
                },
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Continue as guest
            TextButton(onClick = onContinueAsGuest) {
                Text("ادامه به‌عنوان مهمان", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LoginForm(
    isLoading: Boolean,
    onSubmit: (String, String) -> Unit,
    onForgotPassword: () -> Unit,
    onSwitchToOtp: (String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("ایمیل") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("گذرواژه") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "پنهان‌کردن" else "نمایش"
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSubmit(email, password) },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("ورود", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        TextButton(
            onClick = { onSwitchToOtp(email) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && email.contains("@")
        ) {
            Text(
                "ورود با کد یک‌بار مصرف ایمیل",
                color = MaterialTheme.colorScheme.primary
            )
        }
        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("گذرواژه را فراموش کرده‌اید؟", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SignupForm(
    isLoading: Boolean,
    onSubmit: (email: String, password: String, name: String?, username: String?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("نام (اختیاری)") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("ایمیل") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("گذرواژه (حداقل ۶ کاراکتر)") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "پنهان‌کردن" else "نمایش"
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSubmit(email, password, name.ifBlank { null }, username.ifBlank { null }) },
            enabled = !isLoading && email.contains("@") && password.length >= 6,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("ساخت حساب رایگان", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun OtpForm(
    email: String,
    issuedCode: String?,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var resendSeconds by remember { mutableStateOf(60) }

    // 60-second countdown for resend
    androidx.compose.runtime.LaunchedEffect(email, resendSeconds) {
        if (resendSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            resendSeconds -= 1
        }
    }
    androidx.compose.runtime.LaunchedEffect(email) {
        resendSeconds = 60
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت")
            }
            Text(
                text = "کد یک‌بار مصرف",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "کد ۶ رقمی به $email ارسال شد",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Demo inbox: shows the issued code locally so the user can complete the flow
        // even without a real email backend. Remove this block once a real email
        // integration is in place.
        if (issuedCode != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "کد شما (نسخه دمو)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = issuedCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 8.sp
                    )
                }
            }
        }

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
            label = { Text("کد ۶ رقمی") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSubmit(code) },
            enabled = !isLoading && code.length == 6,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("تأیید و ورود", fontWeight = FontWeight.SemiBold)
        }
        TextButton(
            onClick = { onResend(); resendSeconds = 60 },
            enabled = resendSeconds == 0 && !isLoading
        ) {
            Text(
                if (resendSeconds > 0) "ارسال مجدد در ${resendSeconds}s"
                else "ارسال مجدد کد",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        // Simple "G" logo (no external dependency)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text("G", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text("ادامه با گوگل", fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary
)
