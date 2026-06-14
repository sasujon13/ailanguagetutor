package com.cheradip.ailanguagetutor.core.pack

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class PackUsageEvent(
    val timestampMs: Long = System.currentTimeMillis(),
    val operation: String,
    val requestedLang: String,
    val resolvedPackCode: String,
    val method: String,
)

@Singleton
class PackUsageTracker @Inject constructor() {
    private val _recentEvents = MutableStateFlow<List<PackUsageEvent>>(emptyList())
    val recentEvents: StateFlow<List<PackUsageEvent>> = _recentEvents.asStateFlow()

    fun record(operation: String, requestedLang: String, resolvedPackCode: String, method: String) {
        _recentEvents.update { events ->
            (
                listOf(
                    PackUsageEvent(
                        operation = operation,
                        requestedLang = requestedLang,
                        resolvedPackCode = resolvedPackCode,
                        method = method,
                    ),
                ) + events
                ).take(100)
        }
    }

    fun summary(): Map<String, Int> =
        _recentEvents.value.groupingBy { it.resolvedPackCode }.eachCount()
}
