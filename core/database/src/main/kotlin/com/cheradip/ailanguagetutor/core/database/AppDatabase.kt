package com.cheradip.ailanguagetutor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cheradip.ailanguagetutor.core.database.dao.AiCacheDao
import com.cheradip.ailanguagetutor.core.database.dao.DocumentDao
import com.cheradip.ailanguagetutor.core.database.dao.DocumentPageDao
import com.cheradip.ailanguagetutor.core.database.dao.LanguagePackDao
import com.cheradip.ailanguagetutor.core.database.dao.LearningActivityDao
import com.cheradip.ailanguagetutor.core.database.dao.SavedWordDao
import com.cheradip.ailanguagetutor.core.database.dao.TranslationCacheDao
import com.cheradip.ailanguagetutor.core.database.dao.TrialStateDao
import com.cheradip.ailanguagetutor.core.database.entity.AiCacheEntity
import com.cheradip.ailanguagetutor.core.database.entity.AppliedPromoEntity
import com.cheradip.ailanguagetutor.core.database.entity.DocumentEntity
import com.cheradip.ailanguagetutor.core.database.entity.DocumentPageEntity
import com.cheradip.ailanguagetutor.core.database.entity.LanguagePackStateEntity
import com.cheradip.ailanguagetutor.core.database.entity.LearningActivityEntity
import com.cheradip.ailanguagetutor.core.database.entity.ReferralCacheEntity
import com.cheradip.ailanguagetutor.core.database.entity.SavedWordEntity
import com.cheradip.ailanguagetutor.core.database.entity.SubscriptionEntitlementEntity
import com.cheradip.ailanguagetutor.core.database.entity.TranslationCacheEntity
import com.cheradip.ailanguagetutor.core.database.entity.TrialStateEntity

@Database(
    entities = [
        DocumentEntity::class,
        DocumentPageEntity::class,
        SavedWordEntity::class,
        ReferralCacheEntity::class,
        AppliedPromoEntity::class,
        SubscriptionEntitlementEntity::class,
        LanguagePackStateEntity::class,
        TranslationCacheEntity::class,
        LearningActivityEntity::class,
        AiCacheEntity::class,
        TrialStateEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun documentPageDao(): DocumentPageDao
    abstract fun savedWordDao(): SavedWordDao
    abstract fun languagePackDao(): LanguagePackDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun learningActivityDao(): LearningActivityDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun trialStateDao(): TrialStateDao
}
