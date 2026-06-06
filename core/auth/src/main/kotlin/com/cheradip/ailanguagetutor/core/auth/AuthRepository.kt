package com.cheradip.ailanguagetutor.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.network.AiltAuthService
import com.cheradip.ailanguagetutor.core.network.AuthLoginRequest
import com.cheradip.ailanguagetutor.core.network.OtpRequest
import com.cheradip.ailanguagetutor.core.network.OtpVerifyRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfig: AppConfig,
    private val authService: AiltAuthService,
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

    suspend fun login(username: String, password: String): Result<AuthUser> {
        val remote = runCatching {
            authService.login(AuthLoginRequest(username, password))
        }.getOrNull()

        val user = remote?.let {
            AuthUser(it.email, it.role, it.whatsapp, it.sessionToken)
        } ?: localLogin(username, password)

        return user?.let {
            persistUser(it)
            Result.success(it)
        } ?: Result.failure(IllegalArgumentException("Invalid credentials"))
    }

    suspend fun requestOtp(target: String): Result<Unit> =
        runCatching { authService.register(OtpRequest(target)) }
            .recoverCatching { Unit }

    suspend fun verifyEmail(target: String, code: String): Result<AuthUser> =
        runCatching {
            val resp = authService.verifyEmail(OtpVerifyRequest(target, code))
            AuthUser(resp.email, resp.role, resp.whatsapp, resp.sessionToken).also { persistUser(it) }
        }

    suspend fun verifyWhatsApp(target: String, code: String): Result<AuthUser> =
        runCatching {
            val resp = authService.verifyWhatsApp(OtpVerifyRequest(target, code))
            AuthUser(resp.email, resp.role, resp.whatsapp, resp.sessionToken).also { persistUser(it) }
        }

    suspend fun logout() {
        context.authDataStore.edit { it.clear() }
    }

    private suspend fun persistUser(user: AuthUser) {
        context.authDataStore.edit { prefs ->
            prefs[keyEmail] = user.email
            prefs[keyRole] = user.role
            user.whatsapp?.let { prefs[keyWhatsapp] = it }
            user.sessionToken?.let { prefs[keySession] = it }
        }
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
            username.startsWith("+") && password.length >= 6 ->
                AuthUser(email = "$username@phone.local", role = "user", whatsapp = username)
            else -> null
        }
    }
}
