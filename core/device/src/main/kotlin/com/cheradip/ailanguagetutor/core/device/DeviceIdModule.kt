package com.cheradip.ailanguagetutor.core.device

import com.cheradip.ailanguagetutor.core.common.DeviceIdProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDeviceIdProvider @Inject constructor(
    private val fingerprint: DeviceFingerprintProvider,
) : DeviceIdProvider {
    override fun deviceId(): String = fingerprint.deviceId()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceIdModule {
    @Binds
    abstract fun bindDeviceIdProvider(impl: AndroidDeviceIdProvider): DeviceIdProvider
}
