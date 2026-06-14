package com.cheradip.ailanguagetutor.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** True when the device has a validated internet connection. */
fun Context.hasValidatedInternet(): Boolean {
    val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivity.activeNetwork ?: return false
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
