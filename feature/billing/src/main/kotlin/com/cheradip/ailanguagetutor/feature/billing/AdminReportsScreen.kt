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
import androidx.compose.material3.Switch
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
import com.cheradip.ailanguagetutor.core.billing.AdminEarningsSnapshot
import com.cheradip.ailanguagetutor.core.billing.AdminReportsRepository
import com.cheradip.ailanguagetutor.core.billing.AdminReportsSnapshot
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.core.network.AdminReportSettingsDto
import com.cheradip.ailanguagetutor.core.network.AdminReportsDebugResponse
import com.cheradip.ailanguagetutor.core.pack.PackUsageTracker
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
    private val packUsageTracker: PackUsageTracker,
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

    private val _reportSettings = MutableStateFlow(AdminReportSettingsDto())
    val reportSettings: StateFlow<AdminReportSettingsDto> = _reportSettings.asStateFlow()

    private val _debugReports = MutableStateFlow<AdminReportsDebugResponse?>(null)
    val debugReports: StateFlow<AdminReportsDebugResponse?> = _debugReports.asStateFlow()

    private val _earningsPeriod = MutableStateFlow(EarningsReportPeriod.MONTHLY)
    val earningsPeriod: StateFlow<EarningsReportPeriod> = _earningsPeriod.asStateFlow()

    private val _earningsFromMs = MutableStateFlow<Long?>(null)
    val earningsFromMs: StateFlow<Long?> = _earningsFromMs.asStateFlow()

    private val _earningsToMs = MutableStateFlow<Long?>(null)
    val earningsToMs: StateFlow<Long?> = _earningsToMs.asStateFlow()

    private val _earningsReport = MutableStateFlow<AdminEarningsSnapshot?>(null)
    val earningsReport: StateFlow<AdminEarningsSnapshot?> = _earningsReport.asStateFlow()

    private val _earningsLoading = MutableStateFlow(false)
    val earningsLoading: StateFlow<Boolean> = _earningsLoading.asStateFlow()

    private val _earningsError = MutableStateFlow<String?>(null)
    val earningsError: StateFlow<String?> = _earningsError.asStateFlow()

    val clientPackUsage = packUsageTracker.recentEvents

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _debugReports.value = null
            _homeAiUrl.value = homeAiSettings.getBaseUrl()

            adminReportsRepository.fetchReportSettings()
                .onSuccess { _reportSettings.value = it }
                .onFailure { _reportSettings.value = AdminReportSettingsDto() }

            val settings = _reportSettings.value

            if (settings.cloudReportsEnabled) {
                adminReportsRepository.fetchReports()
                    .onSuccess {
                        _cloud.value = it
                        _reportSettings.value = AdminReportSettingsDto(
                            cloudReportsEnabled = it.cloudReportsEnabled,
                            homeAiReportsEnabled = it.homeAiReportsEnabled,
                            debugReportsEnabled = it.debugReportsEnabled,
                        )
                    }
                    .onFailure { _error.value = it.message ?: "Could not load cloud reports" }
            } else {
                _cloud.value = null
            }

            if (settings.homeAiReportsEnabled) {
                _homeAiReachable.value = homeAiService.isReachable()
                _homeAi.value = if (_homeAiReachable.value) homeAiService.fetchAdminStatus() else null
            } else {
                _homeAiReachable.value = false
                _homeAi.value = null
            }

            if (settings.debugReportsEnabled) {
                adminReportsRepository.fetchDebugReports()
                    .onSuccess { _debugReports.value = it }
                    .onFailure {
                        _debugReports.value = null
                    }
            }

            _loading.value = false
            if (settings.cloudReportsEnabled) {
                loadEarningsReport()
            }
        }
    }

    fun selectEarningsPeriod(period: EarningsReportPeriod) {
        _earningsPeriod.value = period
        if (period == EarningsReportPeriod.CUSTOM && _earningsFromMs.value == null) {
            val now = System.currentTimeMillis()
            _earningsToMs.value = now
            _earningsFromMs.value = now - 30L * 24 * 60 * 60 * 1000
        }
        if (period != EarningsReportPeriod.CUSTOM) {
            loadEarningsReport()
        }
    }

    fun setEarningsFrom(ms: Long) {
        _earningsFromMs.value = ms
    }

    fun setEarningsTo(ms: Long) {
        _earningsToMs.value = ms
    }

    fun loadEarningsReport() {
        viewModelScope.launch {
            _earningsLoading.value = true
            _earningsError.value = null
            val period = _earningsPeriod.value
            if (period == EarningsReportPeriod.CUSTOM) {
                val fromMs = _earningsFromMs.value
                val toMs = _earningsToMs.value
                if (fromMs == null || toMs == null) {
                    _earningsError.value = "Select both From and To dates for custom range"
                    _earningsLoading.value = false
                    return@launch
                }
                if (fromMs > toMs) {
                    _earningsError.value = "From date must be on or before To date"
                    _earningsLoading.value = false
                    return@launch
                }
            }
            val from = _earningsFromMs.value?.let { formatEarningsApiDate(it) }
                .takeIf { period == EarningsReportPeriod.CUSTOM }
            val to = _earningsToMs.value?.let { formatEarningsApiDate(it) }
                .takeIf { period == EarningsReportPeriod.CUSTOM }
            adminReportsRepository.fetchEarningsReport(period.apiValue, from, to)
                .onSuccess { _earningsReport.value = it }
                .onFailure { _earningsError.value = it.message ?: "Could not load earnings" }
            _earningsLoading.value = false
        }
    }

    fun setCloudReportsEnabled(enabled: Boolean) {
        updateSettings(cloudReportsEnabled = enabled)
    }

    fun setHomeAiReportsEnabled(enabled: Boolean) {
        updateSettings(homeAiReportsEnabled = enabled)
    }

    fun setDebugReportsEnabled(enabled: Boolean) {
        updateSettings(debugReportsEnabled = enabled)
    }

    private fun updateSettings(
        cloudReportsEnabled: Boolean? = null,
        homeAiReportsEnabled: Boolean? = null,
        debugReportsEnabled: Boolean? = null,
    ) {
        viewModelScope.launch {
            adminReportsRepository.updateReportSettings(
                cloudReportsEnabled = cloudReportsEnabled,
                homeAiReportsEnabled = homeAiReportsEnabled,
                debugReportsEnabled = debugReportsEnabled,
            ).onSuccess { updated ->
                _reportSettings.value = updated
                refresh()
            }.onFailure { err ->
                _error.value = err.message ?: "Could not update report settings"
            }
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
    val reportSettings by viewModel.reportSettings.collectAsStateWithLifecycle()
    val debugReports by viewModel.debugReports.collectAsStateWithLifecycle()
    val clientPackUsage by viewModel.clientPackUsage.collectAsStateWithLifecycle()
    val earningsPeriod by viewModel.earningsPeriod.collectAsStateWithLifecycle()
    val earningsFromMs by viewModel.earningsFromMs.collectAsStateWithLifecycle()
    val earningsToMs by viewModel.earningsToMs.collectAsStateWithLifecycle()
    val earningsReport by viewModel.earningsReport.collectAsStateWithLifecycle()
    val earningsLoading by viewModel.earningsLoading.collectAsStateWithLifecycle()
    val earningsError by viewModel.earningsError.collectAsStateWithLifecycle()

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

            SectionHeader(title = "Report services")
            ReportCard {
                ReportToggleRow(
                    label = "Cloud platform reports",
                    checked = reportSettings.cloudReportsEnabled,
                    onCheckedChange = viewModel::setCloudReportsEnabled,
                )
                ReportToggleRow(
                    label = "Home AI reports",
                    checked = reportSettings.homeAiReportsEnabled,
                    onCheckedChange = viewModel::setHomeAiReportsEnabled,
                )
                ReportToggleRow(
                    label = "Debug reports",
                    checked = reportSettings.debugReportsEnabled,
                    onCheckedChange = viewModel::setDebugReportsEnabled,
                )
                Text(
                    "Each service can be stopped independently. Changes are saved to the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!reportSettings.cloudReportsEnabled) {
                Text(
                    "Cloud report generation is disabled on the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            cloud?.let { report ->
                cloudGeneratedLabel(report.generatedAtMs)
                SectionHeader(title = "Earnings")
                ReportCard {
                    AdminEarningsReportSection(
                        loading = earningsLoading,
                        error = earningsError,
                        report = earningsReport,
                        selectedPeriod = earningsPeriod,
                        customFromMs = earningsFromMs,
                        customToMs = earningsToMs,
                        onPeriodSelected = viewModel::selectEarningsPeriod,
                        onCustomFromSelected = viewModel::setEarningsFrom,
                        onCustomToSelected = viewModel::setEarningsTo,
                        onLoad = viewModel::loadEarningsReport,
                    )
                }
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
                    MetricRow("Referral pending (uncleared)", "$${"%.2f".format(report.referralPendingCommissionUsd)}")
                }
                SectionHeader(title = "Language packs")
                ReportCard {
                    MetricRow("Active catalog packs", report.languagePackCatalogActive.toString())
                    if (report.languagePackRows.isEmpty()) {
                        Text(
                            "No language packs in server catalog.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        report.languagePackRows.forEach { pack ->
                            MetricRow(
                                pack.code.uppercase(),
                                "v${pack.version} · ${pack.sizeBytes / 1024} KB",
                            )
                        }
                    }
                    if (report.learningActivityByLanguage.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Practice activity by language", style = MaterialTheme.typography.titleSmall)
                        report.learningActivityByLanguage.forEach { row ->
                            MetricRow(row.languageCode.uppercase(), row.count.toString())
                        }
                    }
                    if (clientPackUsage.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("This device — recent pack lookups", style = MaterialTheme.typography.titleSmall)
                        clientPackUsage.take(8).forEach { event ->
                            MetricRow(
                                "${event.operation} (${event.requestedLang})",
                                "${event.resolvedPackCode} · ${event.method}",
                            )
                        }
                    }
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
            if (!reportSettings.homeAiReportsEnabled) {
                Text(
                    "Home AI report fetching is disabled on the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

            if (reportSettings.debugReportsEnabled) {
                SectionHeader(title = "Debug reports")
                ReportCard {
                    val debug = debugReports
                    if (debug == null) {
                        Text(
                            "Debug endpoint enabled but no data loaded yet. Tap Refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        debug.languagePacks.forEach { pack ->
                            MetricRow(
                                pack.code.uppercase(),
                                "v${pack.version}${if (pack.sizeBytes > 0) " · active" else ""}",
                            )
                        }
                        if (debug.cloudAiProviderErrors.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Provider errors", style = MaterialTheme.typography.titleSmall)
                            debug.cloudAiProviderErrors.forEach { row ->
                                MetricRow(row.id, row.lastError ?: row.health)
                            }
                        }
                        if (debug.learningActivityByLanguage.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("All activity languages", style = MaterialTheme.typography.titleSmall)
                            debug.learningActivityByLanguage.forEach { row ->
                                MetricRow(row.languageCode.uppercase(), row.count.toString())
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Debug reports are disabled on the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun ReportToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
