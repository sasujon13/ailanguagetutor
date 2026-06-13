package com.cheradip.ailanguagetutor.feature.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.auth.AuthRepository
import com.cheradip.ailanguagetutor.core.auth.SignupDetails
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(
    authRepository: AuthRepository,
    onSignedUp: () -> Unit,
    onNavigateLogin: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CheradipScrollScreen(
        modifier = modifier,
        title = "Sign up",
        subtitle = "Create your account — no verification code needed",
        onBack = onBack,
    ) {
        item {
            Text(
                "Use your email address to sign in. Password reset codes are sent from admin@ailanguagetutor.com.",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it })
                Text("I accept Terms & Privacy *", modifier = Modifier.padding(start = 4.dp))
            }
        }

        error?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.error) }
        }

        item {
            IconTextButton(
                label = if (loading) "Creating account…" else "Create account",
                icon = Icons.Default.Verified,
                onClick = {
                    error = validateSignUp(fullName, email, username, password, confirmPassword, termsAccepted)
                    if (error != null) return@IconTextButton
                    scope.launch {
                        loading = true
                        authRepository.signupInit(
                            SignupDetails(
                                fullName = fullName.trim(),
                                email = email.trim(),
                                username = username.trim(),
                                password = password,
                            ),
                        ).onSuccess {
                            error = null
                            onSignedUp()
                        }.onFailure { error = it.message ?: "Signup failed" }
                        loading = false
                    }
                },
                enabled = !loading,
            )
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

private fun validateSignUp(
    fullName: String,
    email: String,
    username: String,
    password: String,
    confirmPassword: String,
    termsAccepted: Boolean,
): String? {
    if (fullName.trim().length < 2) return "Full name is required (2+ characters)"
    if (!email.contains("@")) return "Valid email is required"
    if (username.trim().length < 3) return "Username is required (3+ characters)"
    if (password.length < 8 || !password.any { it.isLetter() } || !password.any { it.isDigit() }) {
        return "Password must be 8+ characters with a letter and a number"
    }
    if (password != confirmPassword) return "Passwords do not match"
    if (!termsAccepted) return "Accept Terms & Privacy to continue"
    return null
}
