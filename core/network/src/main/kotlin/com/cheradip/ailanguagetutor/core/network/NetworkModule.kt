package com.cheradip.ailanguagetutor.core.network

import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        appLocaleInterceptor: AppLocaleInterceptor,
        sessionAuthInterceptor: SessionAuthInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(appLocaleInterceptor)
            .addInterceptor(sessionAuthInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(appConfig: AppConfig, moshi: Moshi, client: OkHttpClient): Retrofit {
        val baseUrl = appConfig.apiBaseUrl.let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides fun provideAuthService(retrofit: Retrofit): AiltAuthService =
        retrofit.create(AiltAuthService::class.java)

    @Provides fun provideDeviceService(retrofit: Retrofit): AiltDeviceService =
        retrofit.create(AiltDeviceService::class.java)

    @Provides fun provideBillingService(retrofit: Retrofit): AiltBillingService =
        retrofit.create(AiltBillingService::class.java)

    @Provides fun providePromoService(retrofit: Retrofit): AiltPromoService =
        retrofit.create(AiltPromoService::class.java)

    @Provides fun provideReferralService(retrofit: Retrofit): AiltReferralService =
        retrofit.create(AiltReferralService::class.java)

    @Provides fun provideLanguageService(retrofit: Retrofit): AiltLanguageService =
        retrofit.create(AiltLanguageService::class.java)

    @Provides fun provideAdminService(retrofit: Retrofit): AiltAdminService =
        retrofit.create(AiltAdminService::class.java)

    @Provides fun provideAiService(retrofit: Retrofit): AiltAiService =
        retrofit.create(AiltAiService::class.java)

    @Provides fun provideLearningService(retrofit: Retrofit): AiltLearningService =
        retrofit.create(AiltLearningService::class.java)
}
