package com.cheradip.ailanguagetutor.di

import com.cheradip.ailanguagetutor.BuildConfig
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.common.SessionTokenHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = object : AppConfig {
        override val apiBaseUrl: String = BuildConfig.API_BASE_URL
        override val adminSeedPassword: String = BuildConfig.ADMIN_SEED_PASSWORD
        override val homeAiBaseUrl: String = BuildConfig.HOME_AI_BASE_URL
        override val homeAiTimeoutMs: Long = BuildConfig.HOME_AI_TIMEOUT_MS
        override val cloudApiTimeoutMs: Long = BuildConfig.CLOUD_API_TIMEOUT_MS
    }

    @Provides
    @Singleton
    fun provideSessionTokenHolder(): SessionTokenHolder = SessionTokenHolder()
}
