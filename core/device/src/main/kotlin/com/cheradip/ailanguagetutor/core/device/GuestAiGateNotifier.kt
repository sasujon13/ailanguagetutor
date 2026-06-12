package com.cheradip.ailanguagetutor.core.device

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Emits when a guest must sign in before further AI use. */
@Singleton
class GuestAiGateNotifier @Inject constructor() {
    private val _loginRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginRequired: SharedFlow<Unit> = _loginRequired.asSharedFlow()

    fun notifyLoginRequired() {
        _loginRequired.tryEmit(Unit)
    }
}
