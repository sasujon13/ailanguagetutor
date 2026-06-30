package com.cheradip.ailanguagetutor

import android.app.Application
import com.cheradip.ailanguagetutor.core.device.DeviceIdBootstrap
import com.cheradip.ailanguagetutor.core.network.ApiBaseUrlInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AiLanguageTutorApp : Application() {
    @Inject lateinit var apiBaseUrlInitializer: ApiBaseUrlInitializer
    @Inject lateinit var deviceIdBootstrap: DeviceIdBootstrap
}
