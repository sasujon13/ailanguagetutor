package com.cheradip.ailanguagetutor.core.common

/** Stable per-install device id for auth and trial tracking. */
interface DeviceIdProvider {
    fun deviceId(): String
}
