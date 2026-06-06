package com.cheradip.ailanguagetutor.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.auth.AuthRepository
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onNavigateSignUp: (() -> Unit)? = null,
    subtitle: String = "Email or WhatsApp",
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CheradipScrollScreen(
        modifier = modifier,
        title = "Sign in",
        subtitle = subtitle,
        onBack = onBack,
    ) {
        item {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Email or WhatsApp") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Email, contentDescription = null) },
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Lock, contentDescription = null) },
            )
        }
        error?.let { msg ->
            item {
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            IconTextButton(
                label = if (loading) "Signing in…" else "Login",
                icon = Icons.Default.Login,
                onClick = {
                    scope.launch {
                        loading = true
                        authRepository.login(username.trim(), password)
                            .onSuccess { onLoggedIn() }
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = !loading && username.isNotBlank() && password.isNotBlank(),
            )
        }
        if (onNavigateSignUp != null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                IconTextButton(
                    label = "Sign up",
                    icon = Icons.Default.PersonAdd,
                    onClick = onNavigateSignUp,
                    filled = false,
                )
            }
        }
        item {
            Text(
                "Only one device can be signed in at a time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
