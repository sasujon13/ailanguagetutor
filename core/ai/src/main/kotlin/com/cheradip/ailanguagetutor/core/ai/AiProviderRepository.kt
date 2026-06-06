package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.network.AiProviderStatusDto
import com.cheradip.ailanguagetutor.core.network.AiProvidersStatusResponse
import com.cheradip.ailanguagetutor.core.network.AiRoutingPolicyDto
import com.cheradip.ailanguagetutor.core.network.AiRoutingPolicyUpdateRequest
import com.cheradip.ailanguagetutor.core.network.AiltAdminService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AiProviderTier { FREE, PAID }

enum class AiProviderHealth {
    HEALTHY,
    DEGRADED,
    EXHAUSTED,
    DISABLED,
    ERROR,
}

enum class AiRoutingMode(val apiValue: String, val label: String) {
    RANDOM_FREE("random_free", "Random free pool"),
    RANDOM_ALL("random_all", "Random all providers"),
    PRIORITY_FALLBACK("priority_fallback", "Free first, then paid"),
    PAID_ONLY("paid_only", "Paid only"),
    ;

    companion object {
        fun fromApi(value: String): AiRoutingMode =
            entries.firstOrNull { it.apiValue == value } ?: RANDOM_FREE
    }
}

data class AiProviderStatus(
    val id: String,
    val displayName: String,
    val tier: AiProviderTier,
    val health: AiProviderHealth,
    val quotaUsedPercent: Int,
    val requestsToday: Int,
    val quotaDailyLimit: Int?,
    val lastError: String?,
    val lastUsedAt: Long?,
    val enabled: Boolean,
)

data class AiProvidersDashboard(
    val providers: List<AiProviderStatus>,
    val routingMode: AiRoutingMode,
    val preferPaidWhenFreeExhausted: Boolean,
    val totalRequestsToday: Int,
    val freePoolAvailable: Boolean,
    val recommendPaidUpgrade: Boolean,
    val summary: String,
    val isLiveFromServer: Boolean,
)

/**
 * Server-side multi-provider router (Gemini, Claude, OpenAI, etc.) — keys never in APK.
 * Admin sees quota/health; routing mode controls free random vs paid fallback.
 */
@Singleton
class AiProviderRepository @Inject constructor(
    private val adminService: AiltAdminService,
) {
    private val _lastProviderUsed = MutableStateFlow<String?>(null)
    val lastProviderUsed: StateFlow<String?> = _lastProviderUsed.asStateFlow()

    suspend fun fetchDashboard(): AiProvidersDashboard =
        runCatching { adminService.aiProviderStatus() }
            .map { it.toDashboard(isLive = true) }
            .getOrElse { mockDashboard() }

    suspend fun updateRoutingMode(mode: AiRoutingMode, preferPaidWhenFreeExhausted: Boolean): Result<AiRoutingMode> =
        runCatching {
            adminService.updateAiRouting(
                AiRoutingPolicyUpdateRequest(
                    mode = mode.apiValue,
                    preferPaidWhenFreeExhausted = preferPaidWhenFreeExhausted,
                ),
            )
        }.map { AiRoutingMode.fromApi(it.mode) }
            .recoverCatching {
                // Offline dev: accept local change
                mode
            }

    suspend fun toggleProvider(providerId: String, enabled: Boolean): Result<Unit> =
        runCatching {
            adminService.toggleAiProvider(providerId, com.cheradip.ailanguagetutor.core.network.AiProviderToggleRequest(enabled))
        }.map { }

    fun recordProviderUsed(providerId: String?) {
        if (!providerId.isNullOrBlank()) _lastProviderUsed.value = providerId
    }

    private val _lastFallbackReason = MutableStateFlow<String?>(null)
    val lastFallbackReason: StateFlow<String?> = _lastFallbackReason.asStateFlow()

    fun recordFallback(reason: String) {
        _lastFallbackReason.value = reason
    }

    private fun AiProvidersStatusResponse.toDashboard(isLive: Boolean): AiProvidersDashboard {
        val mode = AiRoutingMode.fromApi(routingPolicy.mode)
        return AiProvidersDashboard(
            providers = providers.map { it.toDomain() },
            routingMode = mode,
            preferPaidWhenFreeExhausted = routingPolicy.preferPaidWhenFreeExhausted,
            totalRequestsToday = totalRequestsToday,
            freePoolAvailable = freePoolAvailable,
            recommendPaidUpgrade = recommendPaidUpgrade,
            summary = summary ?: buildSummary(providers.map { it.toDomain() }, mode),
            isLiveFromServer = isLive,
        )
    }

    private fun AiProviderStatusDto.toDomain() = AiProviderStatus(
        id = id,
        displayName = displayName,
        tier = when (tier.lowercase()) {
            "paid" -> AiProviderTier.PAID
            else -> AiProviderTier.FREE
        },
        health = when (health.lowercase()) {
            "degraded" -> AiProviderHealth.DEGRADED
            "exhausted" -> AiProviderHealth.EXHAUSTED
            "disabled" -> AiProviderHealth.DISABLED
            "error" -> AiProviderHealth.ERROR
            else -> AiProviderHealth.HEALTHY
        },
        quotaUsedPercent = quotaUsedPercent,
        requestsToday = requestsToday,
        quotaDailyLimit = quotaDailyLimit,
        lastError = lastError,
        lastUsedAt = lastUsedAt,
        enabled = enabled,
    )

    private fun mockDashboard(): AiProvidersDashboard {
        val providers = listOf(
            mockProvider("gemini", "Google Gemini 2.5 Flash", AiProviderTier.FREE, AiProviderHealth.HEALTHY, 42, 420, 1000),
            mockProvider("openai", "OpenAI GPT-4o mini", AiProviderTier.FREE, AiProviderHealth.DEGRADED, 88, 880, 1000, "Approaching free tier limit"),
            mockProvider("claude", "Anthropic Claude Haiku", AiProviderTier.FREE, AiProviderHealth.HEALTHY, 35, 350, 1000),
            mockProvider("groq", "Groq Llama 3", AiProviderTier.FREE, AiProviderHealth.HEALTHY, 22, 220, 1000),
            mockProvider("mistral", "Mistral Small", AiProviderTier.FREE, AiProviderHealth.EXHAUSTED, 100, 500, 500, "Daily free quota exhausted"),
            mockProvider("openai_paid", "OpenAI GPT-4o (paid)", AiProviderTier.PAID, AiProviderHealth.DISABLED, 0, 0, null, enabled = false),
            mockProvider("claude_paid", "Claude Sonnet (paid)", AiProviderTier.PAID, AiProviderHealth.HEALTHY, 12, 120, null),
        )
        val mode = AiRoutingMode.RANDOM_FREE
        return AiProvidersDashboard(
            providers = providers,
            routingMode = mode,
            preferPaidWhenFreeExhausted = true,
            totalRequestsToday = providers.sumOf { it.requestsToday },
            freePoolAvailable = providers.any { it.tier == AiProviderTier.FREE && it.health == AiProviderHealth.HEALTHY && it.enabled },
            recommendPaidUpgrade = providers.any { it.tier == AiProviderTier.FREE && it.health == AiProviderHealth.EXHAUSTED },
            summary = buildSummary(providers, mode) + " (offline preview — connect server for live stats)",
            isLiveFromServer = false,
        )
    }

    private fun mockProvider(
        id: String,
        name: String,
        tier: AiProviderTier,
        health: AiProviderHealth,
        quotaPct: Int,
        requests: Int,
        limit: Int?,
        lastError: String? = null,
        enabled: Boolean = true,
    ) = AiProviderStatus(
        id = id,
        displayName = name,
        tier = tier,
        health = health,
        quotaUsedPercent = quotaPct,
        requestsToday = requests,
        quotaDailyLimit = limit,
        lastError = lastError,
        lastUsedAt = System.currentTimeMillis() - (id.hashCode() % 3600) * 1000L,
        enabled = enabled,
    )

    private fun buildSummary(providers: List<AiProviderStatus>, mode: AiRoutingMode): String {
        val freeOk = providers.count { it.tier == AiProviderTier.FREE && it.enabled && it.health == AiProviderHealth.HEALTHY }
        val exhausted = providers.count { it.health == AiProviderHealth.EXHAUSTED }
        return when {
            freeOk >= 2 -> "${mode.label}: $freeOk free providers ready. Requests rotate randomly."
            exhausted > 0 -> "Free pool strained ($exhausted exhausted). Consider enabling paid providers."
            else -> "${mode.label}: monitor provider quotas below."
        }
    }
}
