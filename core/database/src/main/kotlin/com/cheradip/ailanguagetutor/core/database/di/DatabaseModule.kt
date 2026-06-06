package com.cheradip.ailanguagetutor.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ailanguagetutor.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDocumentDao(db: AppDatabase) = db.documentDao()

    @Provides
    fun provideDocumentPageDao(db: AppDatabase) = db.documentPageDao()

    @Provides
    fun provideSavedWordDao(db: AppDatabase) = db.savedWordDao()

    @Provides
    fun provideLanguagePackDao(db: AppDatabase) = db.languagePackDao()

    @Provides
    fun provideTranslationCacheDao(db: AppDatabase) = db.translationCacheDao()

    @Provides
    fun provideLearningActivityDao(db: AppDatabase) = db.learningActivityDao()

    @Provides
    fun provideAiCacheDao(db: AppDatabase) = db.aiCacheDao()

    @Provides
    fun provideTrialStateDao(db: AppDatabase) = db.trialStateDao()
}
