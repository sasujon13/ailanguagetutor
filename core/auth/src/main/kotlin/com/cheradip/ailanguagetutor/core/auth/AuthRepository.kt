package com.cheradip.ailanguagetutor.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.common.DeviceIdProvider
import com.cheradip.ailanguagetutor.core.common.SessionTokenHolder
import com.cheradip.ailanguagetutor.core.network.AiltAuthService
import com.cheradip.ailanguagetutor.core.network.AuthLoginRequest
import com.cheradip.ailanguagetutor.core.network.EmailChangeConfirmRequest
import com.cheradip.ailanguagetutor.core.network.EmailChangeSendRequest
import com.cheradip.ailanguagetutor.core.network.NetworkErrorFormatter
import com.cheradip.ailanguagetutor.core.network.PasswordUpdateConfirmRequest
import com.cheradip.ailanguagetutor.core.network.PasswordUpdateSendRequest
import com.cheradip.ailanguagetutor.core.network.RecoveryResetRequest
import com.cheradip.ailanguagetutor.core.network.RecoverySendRequest
import com.cheradip.ailanguagetutor.core.network.SignupInitRequest
import com.cheradip.ailanguagetutor.core.network.loginFailureMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

data class AuthUser(
    val email: String,
    val role: String = "user",
    val whatsapp: String? = null,
    val sessionToken: String? = null,
)

data class SignupDetails(
    val fullName: String,
    val email: String,
    val username: String,
    val password: String,
)

data class PasswordSendResult(
    val message: String,
    val requiresOtp: Boolean,
)

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authService: AiltAuthService,
    private val sessionTokenHolder: SessionTokenHolder,
    private val deviceIdProvider: DeviceIdProvider,
    private val networkErrors: NetworkErrorFormatter,
    private val credentialStore: AuthCredentialStore,
) {
    private val keyEmail = stringPreferencesKey("email")
    private val keyRole = stringPreferencesKey("role")
    private val keyWhatsapp = stringPreferencesKey("whatsapp")
    private val keySession = stringPreferencesKey("session_token")
    private val keyRememberedLogin = stringPreferencesKey("remembered_login")

    val currentUser: Flow<AuthUser?> = context.authDataStore.data.map { prefs ->
        val email = prefs[keyEmail] ?: return@map null
        AuthUser(
            email = email,
            role = prefs[keyRole] ?: "user",
            whatsapp = prefs[keyWhatsapp],
            sessionToken = prefs[keySession],
        )
    }

    private fun deviceId(): String = deviceIdProvider.deviceId()

    suspend fun syncSessionFromStore() {
        val prefs = context.authDataStore.data.first()
        sessionTokenHolder.setToken(prefs[keySession])
    }

    suspend fun rememberedLoginId(): String? =
        context.authDataStore.data.first()[keyRememberedLogin]?.takeIf { it.isNotBlank() }

    /** Restores session from disk, or signs in with saved credentials when the session expired. */
    suspend fun tryAutoLoginIfNeeded(): Boolean {
        val prefs = context.authDataStore.data.first()
        if (!prefs[keySession].isNullOrBlank()) {
            syncSessionFromStore()
            return false
        }
        val loginId = prefs[keyRememberedLogin] ?: return false
        val password = credentialStore.loadPassword() ?: return false
        return login(loginId, password, rememberCredentials = false).isSuccess
    }

    suspend fun login(
        username: String,
        password: String,
        rememberCredentials: Boolean = true,
    ): Result<AuthUser> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalArgumentException(networkErrors.offlineMessage()))
        }
        return try {
            val resp = authService.login(AuthLoginRequest(username.trim(), password, deviceId()))
            val user = AuthUser(resp.email, resp.role, resp.whatsapp, resp.sessionToken)
            persistUser(user)
            if (rememberCredentials) {
                saveRememberedCredentials(username, password)
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(loginFailureMessage(e, "Login failed")))
        }
    }

    suspend fun signupInit(details: SignupDetails): Result<AuthUser> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalArgumentException(networkErrors.offlineMessage()))
        }
        return try {
        val resp = authService.signupInit(
            SignupInitRequest(
                fullName = details.fullName,
                email = details.email.trim().lowercase(),
                username = details.username.trim(),
                password = details.password,
                deviceId = deviceId(),
            ),
        )
        val token = resp.sessionToken ?: throw IllegalStateException("Signup did not return a session")
        val user = AuthUser(resp.email, resp.role, whatsapp = null, sessionToken = token)
        persistUser(user)
        saveRememberedCredentials(details.email.trim(), details.password)
        Result.success(user)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(networkErrors.present(e, "Signup failed")))
        }
    }

    suspend fun recoverySend(username: String): Result<PasswordSendResult> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalArgumentException(networkErrors.offlineMessage()))
        }
        return try {
        val resp = authService.recoverySend(RecoverySendRequest(username.trim(), deviceId()))
        Result.success(PasswordSendResult(resp.message, resp.requiresOtp))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(networkErrors.present(e, "Could not start password reset")))
        }
    }

    suspend fun recoveryReset(
        username: String,
        newPassword: String,
        otp: String?,
    ): Result<String> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalArgumentException(networkErrors.offlineMessage()))
        }
        return try {
        val resp = authService.recoveryReset(
            RecoveryResetRequest(
                username = username.trim(),
                newPassword = newPassword,
                otp = otp?.trim()?.takeIf { it.isNotBlank() },
                deviceId = deviceId(),
            ),
        )
        Result.success(resp.message)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(networkErrors.present(e, "Password reset failed")))
        }
    }

    suspend fun passwordUpdateSend(currentPassword: String): Result<PasswordSendResult> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalArgumentException(networkErrors.offlineMessage()))
        }
        return try {
        val resp = authService.passwordUpdateSend(
            PasswordUpdateSendRequest(currentPassword, deviceId()),
        )
        Result.success(PasswordSendResult(resp.message, resp.requiresOtp))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(networkErrors.present(e, "Could not start password update")))
        }
    }

    suspend fun passwordUpdateConfirm(
        currentPassword: String,
        newPassword: String,
        otp: String?,
    ): Result<String> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalArgumentException(networkErrors.offlineMessage()))
        }
        return try {
        val resp = authService.passwordUpdateConfirm(
            PasswordUpdateConfirmRequest(
                newPassword = newPassword,
                currentPassword = currentPassword,
                otp = otp?.trim()?.takeIf { it.isNotBlank() },
                deviceId = deviceId(),
            ),
        )
        Result.success(resp.message)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(networkErrors.present(e, "Password update failed")))
        }
    }

    suspend fun emailChangeSend(): Result<PasswordSendResult> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalArgumentException(networkErrors.offlineMessage()))
        }
        return try {
        val resp = authService.emailChangeSend(EmailChangeSendRequest(deviceId()))
        Result.success(PasswordSendResult(resp.message, resp.requiresOtp))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(networkErrors.present(e, "Could not start email change")))
        }
    }

    suspend fun emailChangeConfirm(otp: String?, newEmail: String): Result<AuthUser> {
        if (!networkErrors.isOnline()) {
            return Result.failure(IllegalArgumentException(networkErrors.offlineMessage()))
        }
        return try {
        val resp = authService.emailChangeConfirm(
            EmailChangeConfirmRequest(
                newEmail = newEmail.trim().lowercase(),
                otp = otp?.trim()?.takeIf { it.isNotBlank() },
                deviceId = deviceId(),
            ),
        )
        val current = context.authDataStore.data.first()
        val updated = AuthUser(
            email = resp.email ?: newEmail.trim().lowercase(),
            role = current[keyRole] ?: "user",
            whatsapp = current[keyWhatsapp],
            sessionToken = current[keySession],
        )
        persistUser(updated)
        Result.success(updated)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(networkErrors.present(e, "Email change failed")))
        }
    }

    suspend fun logout() {
        clearRememberedCredentials()
        context.authDataStore.edit { it.clear() }
        sessionTokenHolder.setToken(null)
    }

    private suspend fun saveRememberedCredentials(loginId: String, password: String) {
        val trimmed = loginId.trim()
        if (trimmed.isBlank() || password.isBlank()) return
        credentialStore.savePassword(password)
        context.authDataStore.edit { prefs ->
            prefs[keyRememberedLogin] = trimmed
        }
    }

    private suspend fun clearRememberedCredentials() {
        credentialStore.clearPassword()
        context.authDataStore.edit { prefs ->
            prefs.remove(keyRememberedLogin)
        }
    }

    private suspend fun persistUser(user: AuthUser) {
        context.authDataStore.edit { prefs ->
            prefs[keyEmail] = user.email
            prefs[keyRole] = user.role
            user.whatsapp?.let { prefs[keyWhatsapp] = it }
            user.sessionToken?.let { prefs[keySession] = it }
        }
        sessionTokenHolder.setToken(user.sessionToken)
    }
}
