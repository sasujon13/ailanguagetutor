package com.cheradip.ailanguagetutor.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
fun ForgotPasswordScreen(
    authRepository: AuthRepository,
    onResetComplete: () -> Unit,
    onNavigateLogin: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var step by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var requiresOtp by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CheradipScrollScreen(
        modifier = modifier,
        title = "Forgot password",
        subtitle = when (step) {
            0 -> "Enter your account email"
            else -> if (requiresOtp) "Enter email code" else "Set new password"
        },
        onBack = onBack,
    ) {
        when (step) {
            0 -> {
                item {
                    Text(
                        "Enter the email you use to sign in. A code is sent from admin@ailanguagetutor.com " +
                            "unless you are on the same device you used to register.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Email, null) },
                    )
                }
            }
            else -> {
                statusMessage?.let { msg ->
                    item {
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (requiresOtp) {
                    item {
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { otp = it },
                            label = { Text("Email verification code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New password *") },
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
                        label = { Text("Confirm new password *") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
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
                    label = if (loading) "Checking…" else "Continue",
                    icon = Icons.Default.Lock,
                    onClick = {
                        if (username.isBlank()) {
                            error = "Enter your email"
                            return@IconTextButton
                        }
                        scope.launch {
                            loading = true
                            authRepository.recoverySend(username)
                                .onSuccess { result ->
                                    requiresOtp = result.requiresOtp
                                    statusMessage = result.message
                                    step = 1
                                    error = null
                                }
                                .onFailure { error = it.message ?: "Request failed" }
                            loading = false
                        }
                    },
                    enabled = !loading,
                )
                else -> IconTextButton(
                    label = if (loading) "Resetting…" else "Reset password",
                    icon = Icons.Default.Verified,
                    onClick = {
                        error = validatePasswordReset(newPassword, confirmPassword, requiresOtp, otp)
                        if (error != null) return@IconTextButton
                        scope.launch {
                            loading = true
                            authRepository.recoveryReset(
                                username = username,
                                newPassword = newPassword,
                                otp = otp.takeIf { requiresOtp },
                            ).onSuccess {
                                error = null
                                onResetComplete()
                            }.onFailure { error = it.message ?: "Password reset failed" }
                            loading = false
                        }
                    },
                    enabled = !loading,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            IconTextButton(
                label = "Back to sign in",
                icon = Icons.Default.Lock,
                onClick = onNavigateLogin,
                filled = false,
            )
        }
    }
}

@Composable
fun UpdatePasswordScreen(
    authRepository: AuthRepository,
    onUpdated: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var step by remember { mutableIntStateOf(0) }
    var requiresOtp by remember { mutableStateOf(true) }
    var currentPassword by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CheradipScrollScreen(
        modifier = modifier,
        title = "Update password",
        subtitle = when (step) {
            0 -> "Confirm current password"
            else -> if (requiresOtp) "Enter email code" else "Set new password"
        },
        onBack = onBack,
    ) {
        when (step) {
            0 -> {
                item {
                    Text(
                        "Email verification is required on a new device. Same registration device skips the code.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current password *") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Lock, null) },
                    )
                }
            }
            else -> {
                statusMessage?.let { msg ->
                    item { Text(msg, style = MaterialTheme.typography.bodyMedium) }
                }
                if (requiresOtp) {
                    item {
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { otp = it },
                            label = { Text("Email verification code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New password *") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm new password *") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
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
                    label = if (loading) "Checking…" else "Continue",
                    icon = Icons.Default.Lock,
                    onClick = {
                        if (currentPassword.isBlank()) {
                            error = "Enter your current password"
                            return@IconTextButton
                        }
                        scope.launch {
                            loading = true
                            authRepository.passwordUpdateSend(currentPassword)
                                .onSuccess { result ->
                                    requiresOtp = result.requiresOtp
                                    statusMessage = result.message
                                    step = 1
                                    error = null
                                }
                                .onFailure { error = it.message ?: "Request failed" }
                            loading = false
                        }
                    },
                    enabled = !loading,
                )
                else -> IconTextButton(
                    label = if (loading) "Updating…" else "Update password",
                    icon = Icons.Default.Verified,
                    onClick = {
                        error = validatePasswordReset(newPassword, confirmPassword, requiresOtp, otp)
                        if (error != null) return@IconTextButton
                        scope.launch {
                            loading = true
                            authRepository.passwordUpdateConfirm(
                                currentPassword = currentPassword,
                                newPassword = newPassword,
                                otp = otp.takeIf { requiresOtp },
                            ).onSuccess {
                                error = null
                                onUpdated()
                            }.onFailure { error = it.message ?: "Password update failed" }
                            loading = false
                        }
                    },
                    enabled = !loading,
                )
            }
        }
    }
}

@Composable
fun ChangeEmailScreen(
    authRepository: AuthRepository,
    currentEmail: String,
    onEmailChanged: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var step by remember { mutableIntStateOf(0) }
    var requiresOtp by remember { mutableStateOf(true) }
    var newEmail by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CheradipScrollScreen(
        modifier = modifier,
        title = "Change email",
        subtitle = when {
            step == 0 -> "Verify your current email"
            requiresOtp -> "Confirm new address"
            else -> "Enter new email"
        },
        onBack = onBack,
    ) {
        item {
            Text(
                if (requiresOtp || step == 0) {
                    "Current email: $currentEmail. A code is sent to your current address before it can be changed " +
                        "(unless you are on the same device you used to register)."
                } else {
                    "Current email: $currentEmail. Enter your new email below."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (step == 1) {
            item {
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("New email *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Email, null) },
                )
            }
            if (requiresOtp) {
                item {
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it },
                        label = { Text("Code from current email *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }
        statusMessage?.let { msg ->
            item { Text(msg, style = MaterialTheme.typography.bodySmall) }
        }
        error?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.error) }
        }
        item {
            if (step == 0) {
                IconTextButton(
                    label = if (loading) "Checking…" else "Continue",
                    icon = Icons.Default.Email,
                    onClick = {
                        scope.launch {
                            loading = true
                            authRepository.emailChangeSend()
                                .onSuccess { result ->
                                    requiresOtp = result.requiresOtp
                                    statusMessage = result.message
                                    step = 1
                                    error = null
                                }
                                .onFailure { error = it.message ?: "Could not start email change" }
                            loading = false
                        }
                    },
                    enabled = !loading,
                )
            } else {
                IconTextButton(
                    label = if (loading) "Updating…" else "Update email",
                    icon = Icons.Default.Verified,
                    onClick = {
                        if (!newEmail.contains("@")) {
                            error = "Enter a valid new email"
                            return@IconTextButton
                        }
                        if (requiresOtp && otp.length < 4) {
                            error = "Enter the verification code"
                            return@IconTextButton
                        }
                        scope.launch {
                            loading = true
                            authRepository.emailChangeConfirm(
                                otp = otp.takeIf { requiresOtp },
                                newEmail = newEmail,
                            )
                                .onSuccess {
                                    error = null
                                    onEmailChanged()
                                }
                                .onFailure { error = it.message ?: "Email change failed" }
                            loading = false
                        }
                    },
                    enabled = !loading,
                )
            }
        }
    }
}

private fun validatePasswordReset(
    newPassword: String,
    confirmPassword: String,
    requiresOtp: Boolean,
    otp: String,
): String? {
    if (requiresOtp && otp.length < 4) return "Enter the verification code"
    if (newPassword.length < 8 || !newPassword.any { it.isLetter() } || !newPassword.any { it.isDigit() }) {
        return "Password must be 8+ characters with a letter and a number"
    }
    if (newPassword != confirmPassword) return "Passwords do not match"
    return null
}
