package com.cheradip.ailanguagetutor.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun savePassword(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun loadPassword(): String? = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotEmpty() }

    fun clearPassword() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    private companion object {
        const val PREFS_FILE = "auth_credentials"
        const val KEY_PASSWORD = "password"
    }
}
