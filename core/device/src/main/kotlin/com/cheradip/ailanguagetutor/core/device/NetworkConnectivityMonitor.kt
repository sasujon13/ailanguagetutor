package com.cheradip.ailanguagetutor.core.device

import android.content.Context
import com.cheradip.ailanguagetutor.core.network.hasValidatedInternet
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isOnline(): Boolean = context.hasValidatedInternet()
}
