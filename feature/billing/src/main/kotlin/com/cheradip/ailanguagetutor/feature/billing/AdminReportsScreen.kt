package com.cheradip.ailanguagetutor.feature.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.HomeAiAdminDashboard
import com.cheradip.ailanguagetutor.core.ai.HomeAiService
import com.cheradip.ailanguagetutor.core.ai.HomeAiSettingsRepository
import com.cheradip.ailanguagetutor.core.billing.AdminReportsRepository
import com.cheradip.ailanguagetutor.core.billing.AdminReportsSnapshot
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.ui.components.CheradipScreenEdgePadding
import com.cheradip.ailanguagetutor.ui.components.CheradipTopBar
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AdminReportsViewModel @Inject constructor(
    private val adminReportsRepository: AdminReportsRepository,
    private val homeAiService: HomeAiService,
    private val homeAiSettings: HomeAiSettingsRepository,
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _cloud = MutableStateFlow<AdminReportsSnapshot?>(null)
    val cloud: StateFlow<AdminReportsSnapshot?> = _cloud.asStateFlow()

    private val _homeAi = MutableStateFlow<HomeAiAdminDashboard?>(null)
    val homeAi: StateFlow<HomeAiAdminDashboard?> = _homeAi.asStateFlow()

    private val _homeAiUrl = MutableStateFlow("")
    val homeAiUrl: StateFlow<String> = _homeAiUrl.asStateFlow()

    private val _homeAiReachable = MutableStateFlow(false)
    val homeAiReachable: StateFlow<Boolean> = _homeAiReachable.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _homeAiUrl.value = homeAiSettings.getBaseUrl()
            _homeAiReachable.value = homeAiService.isReachable()
            _homeAi.value = if (_homeAiReachable.value) homeAiService.fetchAdminStatus() else null
            adminReportsRepository.fetchReports()
                .onSuccess { _cloud.value = it }
                .onFailure { _error.value = it.message ?: "Could not load reports" }
            _loading.value = false
        }
    }
}

@Composable
fun AdminReportsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminReportsViewModel = hiltViewModel(),
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val cloud by viewModel.cloud.collectAsStateWithLifecycle()
    val homeAi by viewModel.homeAi.collectAsStateWithLifecycle()
    val homeAiUrl by viewModel.homeAiUrl.collectAsStateWithLifecycle()
    val homeAiReachable by viewModel.homeAiReachable.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CheradipTopBar(
                title = appString("admin_reports_title"),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(CheradipScreenEdgePadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    appString("admin_reports_subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = viewModel::refresh, enabled = !loading) {
                    Text(appString("admin_reports_refresh"))
                }
            }
            if (loading && cloud == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            cloud?.let { report ->
                cloudGeneratedLabel(report.generatedAtMs)
                SectionHeader(title = appString("admin_reports_users"))
                ReportCard {
                    MetricRow(appString("admin_reports_total_users"), report.totalUsers.toString())
                    MetricRow(appString("admin_reports_regular_users"), report.regularUsers.toString())
                    MetricRow(appString("admin_reports_admin_users"), report.adminUsers.toString())
                    MetricRow(appString("admin_reports_email_verified"), report.emailVerifiedUsers.toString())
                    MetricRow(appString("admin_reports_new_7d"), report.newUsers7Days.toString())
                    MetricRow(appString("admin_reports_new_30d"), report.newUsers30Days.toString())
                }
                SectionHeader(title = appString("admin_reports_subscriptions"))
                ReportCard {
                    MetricRow(appString("admin_reports_active_total"), report.activeSubscriptions.toString())
                    MetricRow("Pro", report.activePro.toString())
                    MetricRow("Plus", report.activePlus.toString())
                }
                SectionHeader(title = appString("admin_reports_engagement"))
                ReportCard {
                    MetricRow(appString("admin_reports_learning_activities"), report.learningActivities.toString())
                    MetricRow(appString("admin_reports_device_trials"), report.deviceTrials.toString())
                    MetricRow(appString("admin_reports_guest_ai_uses"), report.guestAiUsesTotal.toString())
                    MetricRow(appString("admin_reports_promo_active"), "${report.promoCodesActive} / ${report.promoCodesTotal}")
                    MetricRow(appString("admin_reports_pending_withdrawals"), report.pendingWithdrawals.toString())
                    MetricRow(appString("admin_reports_referral_balance"), "$${"%.2f".format(report.referralBalanceUsd)}")
                }
                SectionHeader(title = appString("admin_reports_cloud_ai"))
                ReportCard {
                    MetricRow(appString("admin_reports_api_requests_today"), report.cloudAiRequestsToday.toString())
                    MetricRow(appString("admin_reports_routing_mode"), report.cloudAiRoutingMode)
                    if (report.cloudAiProviders.isEmpty()) {
                        Text(
                            appString("admin_reports_no_providers"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        report.cloudAiProviders.forEach { provider ->
                            Text(
                                provider.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            MetricRow(appString("admin_reports_requests"), provider.requestsToday.toString())
                            MetricRow(appString("admin_reports_tier"), provider.tier)
                            MetricRow(appString("admin_reports_health"), provider.health)
                            provider.quotaDailyLimit?.let { limit ->
                                MetricRow(
                                    appString("admin_reports_quota"),
                                    "${provider.quotaUsedPercent}% ($limit/day)",
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
            SectionHeader(title = appString("admin_reports_home_ai"))
            ReportCard {
                MetricRow(appString("admin_reports_home_ai_url"), homeAiUrl)
                MetricRow(
                    appString("admin_reports_home_ai_status"),
                    if (homeAiReachable) appString("admin_reports_online") else appString("admin_reports_offline"),
                )
                if (homeAi == null && !homeAiReachable) {
                    Text(
                        appString("admin_reports_home_ai_unreachable"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    homeAi?.let { status ->
                        MetricRow(appString("admin_reports_backend"), status.inferenceBackend)
                        MetricRow(appString("admin_reports_gpu"), if (status.gpuAvailable) "Yes" else "No")
                        MetricRow(appString("admin_reports_model_loaded"), status.modelLoaded ?: "—")
                        MetricRow(appString("admin_reports_api_requests_total"), status.routesTotal.toString())
                        MetricRow(appString("admin_reports_queue_depth"), status.queueDepth.toString())
                        MetricRow(appString("admin_reports_cache_hit"), "${status.cacheHitRatePct.toInt()}%")
                        MetricRow(
                            appString("admin_reports_rate_limit"),
                            "${status.rateLimitAllowed} ok · ${status.rateLimitRejected} rejected",
                        )
                        status.lastModelUsed?.let {
                            MetricRow(appString("admin_reports_last_model"), it)
                        }
                        status.lastTranslationBackend?.let {
                            MetricRow(appString("admin_reports_last_translation"), it)
                        }
                        if (status.inferenceFallbackCount > 0) {
                            MetricRow(appString("admin_reports_fallbacks"), status.inferenceFallbackCount.toString())
                        }
                        if (status.residentModels.isNotEmpty()) {
                            MetricRow(
                                appString("admin_reports_resident_models"),
                                status.residentModels.joinToString(", "),
                            )
                        }
                        if (status.routesByIntent.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                appString("admin_reports_requests_by_intent"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            status.routesByIntent.entries.sortedByDescending { it.value }.forEach { (intent, count) ->
                                MetricRow(intent.replace('_', ' '), count.toString())
                            }
                        }
                        if (status.modelsUsed.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                appString("admin_reports_models_used"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            status.modelsUsed.entries.sortedByDescending { it.value }.forEach { (model, count) ->
                                MetricRow(model, count.toString())
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun cloudGeneratedLabel(generatedAtMs: Long) {
    if (generatedAtMs <= 0L) return
    val formatted = rememberFormattedTime(generatedAtMs)
    Text(
        "${appString("admin_reports_updated")}: $formatted",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun rememberFormattedTime(ms: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(ms))
}

@Composable
private fun ReportCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
