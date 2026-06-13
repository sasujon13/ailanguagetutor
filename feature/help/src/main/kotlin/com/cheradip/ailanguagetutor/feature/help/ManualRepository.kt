package com.cheradip.ailanguagetutor.feature.help

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun loadManual(type: ManualType): Result<List<ManualBlock>> = runCatching {
        context.assets.open("manuals/${type.assetFileName}").bufferedReader().use { reader ->
            ManualParser.parse(reader.readText())
        }
    }
}
