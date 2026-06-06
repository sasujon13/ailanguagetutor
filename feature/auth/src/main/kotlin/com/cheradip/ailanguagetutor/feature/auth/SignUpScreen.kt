package com.cheradip.ailanguagetutor.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.auth.AuthRepository
import com.cheradip.ailanguagetutor.core.auth.SignupDetails
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(
    authRepository: AuthRepository,
    onSignedUp: () -> Unit,
    onNavigateLogin: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var step by remember { mutableIntStateOf(0) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loginWith by remember { mutableStateOf("email") }
    var termsAccepted by remember { mutableStateOf(false) }
    var emailCode by remember { mutableStateOf("") }
    var whatsappCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CheradipScrollScreen(
        modifier = modifier,
        title = "Sign up",
        subtitle = when (step) {
            0 -> "Step 1 — Account details"
            1 -> "Step 2 — Verify email"
            else -> "Step 3 — Verify WhatsApp"
        },
        onBack = onBack,
    ) {
        when (step) {
            0 -> {
                item {
                    Text(
                        "Required: full name, email, WhatsApp, username, password. " +
                            "Both email and WhatsApp must be verified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Person, null) },
                    )
                }
                item {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Email, null) },
                    )
                }
                item {
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it },
                        label = { Text("WhatsApp (E.164, e.g. +8801…) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Phone, null) },
                    )
                }
                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (8+ chars, letter + number) *") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Lock, null) },
                    )
                }
                item {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm password *") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                item {
                    SectionHeader(title = "Login with")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("email" to "Email", "whatsapp" to "WhatsApp").forEach { (value, label) ->
                            Row(
                                modifier = Modifier.selectable(
                                    selected = loginWith == value,
                                    onClick = { loginWith = value },
                                    role = Role.RadioButton,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = loginWith == value, onClick = null)
                                Text(label)
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it })
                        Text("I accept Terms & Privacy *", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            1 -> {
                item {
                    Text(
                        "Enter the 6-digit code sent to $email",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    OutlinedTextField(
                        value = emailCode,
                        onValueChange = { emailCode = it },
                        label = { Text("Email verification code *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
            else -> {
                item {
                    Text(
                        "Enter the 6-digit code sent to $whatsapp",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    OutlinedTextField(
                        value = whatsappCode,
                        onValueChange = { whatsappCode = it },
                        label = { Text("WhatsApp verification code *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }

        error?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.error) }
        }

        item {
            when (step) {
                0 -> IconTextButton(
                    label = if (loading) "Creating account…" else "Continue to email verification",
                    icon = Icons.Default.Email,
                    onClick = {
                        error = validateStep1(
                            fullName, email, whatsapp, username, password, confirmPassword, termsAccepted,
                        )
                        if (error != null) return@IconTextButton
                        scope.launch {
                            loading = true
                            authRepository.signupInit(
                                SignupDetails(
                                    fullName = fullName.trim(),
                                    email = email.trim(),
                                    whatsapp = whatsapp.trim(),
                                    username = username.trim(),
                                    password = password,
                                    loginWith = loginWith,
                                ),
                            ).onSuccess {
                                step = 1
                                error = null
                            }.onFailure { error = it.message ?: "Signup failed" }
                            loading = false
                        }
                    },
                    enabled = !loading,
                )
                1 -> IconTextButton(
                    label = if (loading) "Verifying…" else "Verify email",
                    icon = Icons.Default.Verified,
                    onClick = {
                        if (emailCode.length < 4) {
                            error = "Enter the verification code"
                            return@IconTextButton
                        }
                        scope.launch {
                            loading = true
                            authRepository.signupVerifyEmail(email, emailCode)
                                .onSuccess {
                                    step = 2
                                    error = null
                                }
                                .onFailure { error = it.message ?: "Invalid code" }
                            loading = false
                        }
                    },
                    enabled = !loading && emailCode.isNotBlank(),
                )
                else -> IconTextButton(
                    label = if (loading) "Finishing…" else "Verify WhatsApp & finish",
                    icon = Icons.Default.Verified,
                    onClick = {
                        if (whatsappCode.length < 4) {
                            error = "Enter the verification code"
                            return@IconTextButton
                        }
                        scope.launch {
                            loading = true
                            authRepository.signupVerifyWhatsApp(whatsapp, whatsappCode)
                                .onSuccess {
                                    error = null
                                    onSignedUp()
                                }
                                .onFailure { error = it.message ?: "Invalid code" }
                            loading = false
                        }
                    },
                    enabled = !loading && whatsappCode.isNotBlank(),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            IconTextButton(
                label = "Already have an account? Sign in",
                icon = Icons.Default.Person,
                onClick = onNavigateLogin,
                filled = false,
            )
        }
    }
}

private fun validateStep1(
    fullName: String,
    email: String,
    whatsapp: String,
    username: String,
    password: String,
    confirmPassword: String,
    termsAccepted: Boolean,
): String? {
    if (fullName.trim().length < 2) return "Full name is required (2+ characters)"
    if (!email.contains("@")) return "Valid email is required"
    if (!whatsapp.startsWith("+")) return "WhatsApp must start with + (E.164)"
    if (username.trim().length < 3) return "Username is required (3+ characters)"
    if (password.length < 8 || !password.any { it.isLetter() } || !password.any { it.isDigit() }) {
        return "Password must be 8+ characters with a letter and a number"
    }
    if (password != confirmPassword) return "Passwords do not match"
    if (!termsAccepted) return "Accept Terms & Privacy to continue"
    return null
}
