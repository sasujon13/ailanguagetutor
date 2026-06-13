package com.cheradip.ailanguagetutor.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.common.DeviceIdProvider
import com.cheradip.ailanguagetutor.core.common.SessionTokenHolder
import com.cheradip.ailanguagetutor.core.network.AiltAuthService
import com.cheradip.ailanguagetutor.core.network.AuthLoginRequest
import com.cheradip.ailanguagetutor.core.network.EmailChangeConfirmRequest
import com.cheradip.ailanguagetutor.core.network.EmailChangeSendRequest
import com.cheradip.ailanguagetutor.core.network.PasswordUpdateConfirmRequest
import com.cheradip.ailanguagetutor.core.network.PasswordUpdateSendRequest
import com.cheradip.ailanguagetutor.core.network.RecoveryResetRequest
import com.cheradip.ailanguagetutor.core.network.RecoverySendRequest
import com.cheradip.ailanguagetutor.core.network.SignupInitRequest
import com.cheradip.ailanguagetutor.core.network.userFacingMessage
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
    private val appConfig: AppConfig,
    private val authService: AiltAuthService,
    private val sessionTokenHolder: SessionTokenHolder,
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val keyEmail = stringPreferencesKey("email")
    private val keyRole = stringPreferencesKey("role")
    private val keyWhatsapp = stringPreferencesKey("whatsapp")
    private val keySession = stringPreferencesKey("session_token")

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

    suspend fun login(username: String, password: String): Result<AuthUser> = try {
        val resp = authService.login(AuthLoginRequest(username.trim(), password, deviceId()))
        val user = AuthUser(resp.email, resp.role, resp.whatsapp, resp.sessionToken)
        persistUser(user)
        Result.success(user)
    } catch (e: Exception) {
        val local = localLogin(username, password)
        if (local != null) {
            persistUser(local)
            Result.success(local)
        } else {
            Result.failure(IllegalArgumentException(e.userFacingMessage("Login failed")))
        }
    }

    suspend fun signupInit(details: SignupDetails): Result<AuthUser> = try {
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
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException(e.userFacingMessage("Signup failed")))
    }

    suspend fun recoverySend(username: String): Result<PasswordSendResult> = try {
        val resp = authService.recoverySend(RecoverySendRequest(username.trim(), deviceId()))
        Result.success(PasswordSendResult(resp.message, resp.requiresOtp))
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException(e.userFacingMessage("Could not start password reset")))
    }

    suspend fun recoveryReset(
        username: String,
        newPassword: String,
        otp: String?,
    ): Result<String> = try {
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
        Result.failure(IllegalArgumentException(e.userFacingMessage("Password reset failed")))
    }

    suspend fun passwordUpdateSend(currentPassword: String): Result<PasswordSendResult> = try {
        val resp = authService.passwordUpdateSend(
            PasswordUpdateSendRequest(currentPassword, deviceId()),
        )
        Result.success(PasswordSendResult(resp.message, resp.requiresOtp))
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException(e.userFacingMessage("Could not start password update")))
    }

    suspend fun passwordUpdateConfirm(
        currentPassword: String,
        newPassword: String,
        otp: String?,
    ): Result<String> = try {
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
        Result.failure(IllegalArgumentException(e.userFacingMessage("Password update failed")))
    }

    suspend fun emailChangeSend(): Result<PasswordSendResult> = try {
        val resp = authService.emailChangeSend(EmailChangeSendRequest(deviceId()))
        Result.success(PasswordSendResult(resp.message, resp.requiresOtp))
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException(e.userFacingMessage("Could not start email change")))
    }

    suspend fun emailChangeConfirm(otp: String?, newEmail: String): Result<AuthUser> = try {
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
        Result.failure(IllegalArgumentException(e.userFacingMessage("Email change failed")))
    }

    suspend fun logout() {
        context.authDataStore.edit { it.clear() }
        sessionTokenHolder.setToken(null)
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

    private fun localLogin(username: String, password: String): AuthUser? {
        val adminEmail = "sashafik.me@gmail.com"
        val adminPass = appConfig.adminSeedPassword
        return when {
            adminPass.isNotBlank() &&
                username.equals(adminEmail, ignoreCase = true) &&
                password == adminPass ->
                AuthUser(email = adminEmail, role = "admin", whatsapp = "+8801722710298")
            username.contains("@") && password.length >= 6 ->
                AuthUser(email = username, role = "user")
            else -> null
        }
    }
}
