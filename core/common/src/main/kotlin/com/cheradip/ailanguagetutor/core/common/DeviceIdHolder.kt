package com.cheradip.ailanguagetutor.core.common

/** Android device id for guest API session affinity (X-Device-Id header). */
class DeviceIdHolder {
    @Volatile
    var deviceId: String? = null
        private set

    fun setDeviceId(value: String?) {
        deviceId = value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
