package com.cheradip.ailanguagetutor.core.device

import com.cheradip.ailanguagetutor.core.common.DeviceIdHolder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdBootstrap @Inject constructor(
    deviceFingerprintProvider: DeviceFingerprintProvider,
    deviceIdHolder: DeviceIdHolder,
) {
    init {
        deviceIdHolder.setDeviceId(deviceFingerprintProvider.deviceId())
    }
}
