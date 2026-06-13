package com.cheradip.ailanguagetutor.feature.scanner.di

import android.content.Context
import com.cheradip.ailanguagetutor.core.image.ScanEditEngine
import com.cheradip.ailanguagetutor.core.image.ScanExportService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Scanner image processing: OpenCV edge detection (lazy) + ML Kit Document Scanner capture.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScannerImageModule {
    @Provides
    @Singleton
    fun provideScanEditEngine(): ScanEditEngine = ScanEditEngine()

    @Provides
    @Singleton
    fun provideScanExportService(@ApplicationContext context: Context): ScanExportService =
        ScanExportService(context)
}
