package com.cheradip.ailanguagetutor.feature.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cheradip.ailanguagetutor.ui.components.CheradipScreenContentPadding
import com.cheradip.ailanguagetutor.ui.components.CheradipScreenEdgePadding
import com.cheradip.ailanguagetutor.ui.theme.CheradipColors
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.AiProviderHealth
import com.cheradip.ailanguagetutor.core.ai.AiProviderRepository
import com.cheradip.ailanguagetutor.core.ai.AiProviderStatus
import com.cheradip.ailanguagetutor.core.ai.AiProviderTier
import com.cheradip.ailanguagetutor.core.ai.AiProvidersDashboard
import com.cheradip.ailanguagetutor.core.ai.AiRoutingMode
import com.cheradip.ailanguagetutor.core.ai.HomeAiAdminDashboard
import com.cheradip.ailanguagetutor.core.ai.HomeAiService
import com.cheradip.ailanguagetutor.core.ai.HomeAiSettingsRepository
import com.cheradip.ailanguagetutor.core.billing.AdminPromoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminPromoViewModel @Inject constructor(
    private val adminPromoRepository: AdminPromoRepository,
) : ViewModel() {
    private val _codes = MutableStateFlow<List<AdminPromoRepository.PromoRow>>(emptyList())
    val codes: StateFlow<List<AdminPromoRepository.PromoRow>> = _codes.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _message.value = null
            _error.value = null
            _codes.value = emptyList()
            adminPromoRepository.listCodes()
                .onSuccess { _codes.value = it.sortedBy { row -> row.code } }
                .onFailure {
                    _codes.value = emptyList()
                    _error.value = it.message ?: "Could not load promo_codes from server"
                }
            _loading.value = false
        }
    }

    fun createCode(
        code: String,
        discountPercent: Int,
        paywallSlot: Int = 2,
        autoApply: Boolean = false,
    ) {
        val normalized = code.trim().uppercase()
        viewModelScope.launch {
            _message.value = null
            _error.value = null
            adminPromoRepository.createCode(
                code = normalized,
                discountPercent = discountPercent,
                autoApply = autoApply,
                paywallSlot = paywallSlot,
            )
                .onSuccess {
                    _message.value = "Saved to promo_codes: $normalized"
                    refresh()
                }
                .onFailure { _error.value = it.message ?: "Could not save promo code" }
        }
    }

    fun updateCode(
        code: String,
        discountPercent: Int,
        active: Boolean,
        paywallSlot: Int,
        autoApply: Boolean,
    ) {
        viewModelScope.launch {
            _message.value = null
            _error.value = null
            adminPromoRepository.updateCode(
                code = code,
                discountPercent = discountPercent,
                active = active,
                autoApply = autoApply,
                paywallSlot = paywallSlot,
            )
                .onSuccess {
                    _message.value = "Updated promo_codes: $code"
                    refresh()
                }
                .onFailure { _error.value = it.message ?: "Could not update promo code" }
        }
    }

    fun clearMessage() {
        _message.value = null
        _error.value = null
    }
}

@HiltViewModel
class AdminHomeAiViewModel @Inject constructor(
    private val homeAiService: HomeAiService,
    private val homeAiSettings: HomeAiSettingsRepository,
) : ViewModel() {
    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _status = MutableStateFlow<HomeAiAdminDashboard?>(null)
    val status: StateFlow<HomeAiAdminDashboard?> = _status.asStateFlow()

    private val _reachable = MutableStateFlow<Boolean?>(null)
    val reachable: StateFlow<Boolean?> = _reachable.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            _baseUrl.value = homeAiSettings.getBaseUrl()
            refresh()
        }
    }

    fun updateBaseUrl(url: String) {
        _baseUrl.value = url
    }

    fun saveBaseUrl() {
        viewModelScope.launch {
            homeAiSettings.setBaseUrl(_baseUrl.value.trim())
            _message.value = "Home AI URL saved"
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _reachable.value = homeAiService.isReachable()
            _status.value = homeAiService.fetchAdminStatus()
            _loading.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@HiltViewModel
class AdminAiViewModel @Inject constructor(
    private val aiProviderRepository: AiProviderRepository,
) : ViewModel() {
    private val _dashboard = MutableStateFlow<AiProvidersDashboard?>(null)
    val dashboard: StateFlow<AiProvidersDashboard?> = _dashboard.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val lastProviderUsed = aiProviderRepository.lastProviderUsed

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _dashboard.value = aiProviderRepository.fetchDashboard()
            _loading.value = false
        }
    }

    fun setRoutingMode(mode: AiRoutingMode) {
        val current = _dashboard.value ?: return
        viewModelScope.launch {
            aiProviderRepository.updateRoutingMode(mode, current.preferPaidWhenFreeExhausted)
                .onSuccess {
                    _dashboard.value = current.copy(routingMode = it)
                    _message.value = "Routing set to ${it.label}"
                }
                .onFailure { _message.value = it.message }
        }
    }

    fun setPreferPaidWhenFreeExhausted(enabled: Boolean) {
        val current = _dashboard.value ?: return
        viewModelScope.launch {
            aiProviderRepository.updateRoutingMode(current.routingMode, enabled)
                .onSuccess {
                    _dashboard.value = current.copy(preferPaidWhenFreeExhausted = enabled)
                }
        }
    }

    fun toggleProvider(id: String, enabled: Boolean) {
        viewModelScope.launch {
            aiProviderRepository.toggleProvider(id, enabled)
                .onSuccess { refresh() }
                .onFailure { _message.value = it.message }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun AdminConsoleScreen(
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    promoViewModel: AdminPromoViewModel = hiltViewModel(),
    aiViewModel: AdminAiViewModel = hiltViewModel(),
    homeAiViewModel: AdminHomeAiViewModel = hiltViewModel(),
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Promo codes") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Cloud AI") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Home AI") })
        }
        when (tab) {
            0 -> AdminPromoTab(Modifier.fillMaxSize(), promoViewModel)
            1 -> AdminAiProvidersTab(Modifier.fillMaxSize(), aiViewModel)
            2 -> AdminHomeAiTab(Modifier.fillMaxSize(), homeAiViewModel)
        }
    }
}

@Composable
private fun AdminPromoTab(
    modifier: Modifier = Modifier,
    viewModel: AdminPromoViewModel,
) {
    val codes by viewModel.codes.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var newCode by remember { mutableStateOf("") }
    var newDiscount by remember { mutableStateOf("20") }
    var newSlot by remember { mutableIntStateOf(2) }
    var newAutoApply by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(CheradipScreenEdgePadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Promo codes", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = viewModel::refresh, enabled = !loading) {
                Text(if (loading) "…" else "Refresh")
            }
        }
        Text(
            "${codes.size} row(s) from promo_codes table. Saves use POST/PATCH on the server only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = newCode,
            onValueChange = { newCode = it.uppercase() },
            label = { Text("New code") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        OutlinedTextField(
            value = newDiscount,
            onValueChange = { newDiscount = it.filter { c -> c.isDigit() }.take(2) },
            label = { Text("Discount %") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Text("Paywall slot", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Referral gate", 1 to "Slot 1 auto", 2 to "Slot 2 manual").forEach { (slot, label) ->
                FilterChip(
                    selected = newSlot == slot,
                    onClick = {
                        newSlot = slot
                        newAutoApply = slot == 1
                    },
                    label = { Text(label) },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Switch(checked = newAutoApply, onCheckedChange = { newAutoApply = it })
            Text("Auto-apply at checkout", modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = {
                val pct = newDiscount.toIntOrNull() ?: return@Button
                if (newCode.isNotBlank()) {
                    viewModel.createCode(newCode, pct, paywallSlot = newSlot, autoApply = newAutoApply)
                    newCode = ""
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Save to promo_codes") }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (loading && codes.isEmpty()) {
            Text("Loading…", style = MaterialTheme.typography.bodyMedium)
        } else if (codes.isEmpty()) {
            Text(
                "No rows in promo_codes. Connect to cloud-api, then add codes below.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            codes.forEach { row ->
                PromoCodeEditCard(row = row, onSave = viewModel::updateCode)
            }
        }
    }
}

@Composable
private fun PromoCodeEditCard(
    row: AdminPromoRepository.PromoRow,
    onSave: (code: String, discountPercent: Int, active: Boolean, paywallSlot: Int, autoApply: Boolean) -> Unit,
) {
    var discount by remember(row.code, row.discountPercent) {
        mutableStateOf(row.discountPercent.toString())
    }
    var active by remember(row.code, row.active) { mutableStateOf(row.active) }
    var paywallSlot by remember(row.code, row.paywallSlot) { mutableIntStateOf(row.paywallSlot) }
    var autoApply by remember(row.code, row.autoApply) { mutableStateOf(row.autoApply) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(row.code, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "promo_codes row",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = active, onCheckedChange = { active = it })
            }
            OutlinedTextField(
                value = discount,
                onValueChange = { discount = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Discount %") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
            )
            Text("Paywall slot", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Gate", 1 to "Slot 1", 2 to "Slot 2").forEach { (slot, label) ->
                    FilterChip(
                        selected = paywallSlot == slot,
                        onClick = {
                            paywallSlot = slot
                            if (slot == 1) autoApply = true
                        },
                        label = { Text(label) },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Switch(checked = autoApply, onCheckedChange = { autoApply = it })
                Text("Auto-apply", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = {
                    val pct = discount.toIntOrNull() ?: return@Button
                    onSave(row.code, pct, active, paywallSlot, autoApply)
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Save to promo_codes") }
        }
    }
}

@Composable
fun AdminAiProvidersTab(
    modifier: Modifier = Modifier,
    viewModel: AdminAiViewModel = hiltViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val lastUsed by viewModel.lastProviderUsed.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.padding(CheradipScreenContentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI API status", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { viewModel.refresh() }, enabled = !loading) {
                    Text(if (loading) "…" else "Refresh")
                }
            }
        }

        if (loading && dashboard == null) {
            item {
                Text("Loading provider status…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        dashboard?.let { dash ->
            item {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (dash.isLiveFromServer) "Live from server" else "Offline preview")
                    },
                )
            }
            if (dash.recommendPaidUpgrade) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Free tier limits reached on some providers. Enable paid keys on the server or switch routing to Paid only.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Text(dash.summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Requests today: ${dash.totalRequestsToday} · Free pool: ${if (dash.freePoolAvailable) "available" else "strained"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                lastUsed?.let {
                    Text("Last AI call routed to: $it", style = MaterialTheme.typography.labelMedium)
                }
                Text("Routing mode", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Server picks a provider randomly (Gemini, Claude, OpenAI, Groq, Mistral…).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiRoutingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = dash.routingMode == mode,
                            onClick = { viewModel.setRoutingMode(mode) },
                            label = { Text(mode.label, maxLines = 1) },
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = dash.preferPaidWhenFreeExhausted,
                        onCheckedChange = viewModel::setPreferPaidWhenFreeExhausted,
                    )
                    Text(
                        "Auto-fallback to paid when free exhausted",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
            items(dash.providers, key = { it.id }) { provider ->
                AiProviderCard(
                    provider = provider,
                    onToggle = { enabled -> viewModel.toggleProvider(provider.id, enabled) },
                )
            }
        }

        message?.let { msg ->
            item {
                Text(msg, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AiProviderCard(
    provider: AiProviderStatus,
    onToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TierChip(provider.tier.name.lowercase())
                        HealthChip(provider.health)
                    }
                }
                Switch(checked = provider.enabled, onCheckedChange = onToggle)
            }
            if (provider.quotaDailyLimit != null) {
                Text(
                    "${provider.requestsToday} / ${provider.quotaDailyLimit} requests today",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                LinearProgressIndicator(
                    progress = { provider.quotaUsedPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = quotaColor(provider.quotaUsedPercent),
                )
            } else {
                Text(
                    "${provider.requestsToday} requests today (paid — no free cap)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            provider.lastError?.let { err ->
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TierChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label.replaceFirstChar { it.uppercase() }) })
}

@Composable
private fun HealthChip(health: AiProviderHealth) {
    val (label, color) = when (health) {
        AiProviderHealth.HEALTHY -> "healthy" to CheradipColors.statusHealthy
        AiProviderHealth.DEGRADED -> "degraded" to CheradipColors.statusWarning
        AiProviderHealth.EXHAUSTED -> "exhausted" to MaterialTheme.colorScheme.error
        AiProviderHealth.DISABLED -> "disabled" to MaterialTheme.colorScheme.outline
        AiProviderHealth.ERROR -> "error" to MaterialTheme.colorScheme.error
    }
    Text(label, color = color, style = MaterialTheme.typography.labelSmall)
}

private fun quotaColor(percent: Int): androidx.compose.ui.graphics.Color = when {
    percent >= 95 -> CheradipColors.statusCritical
    percent >= 80 -> CheradipColors.statusWarning
    else -> CheradipColors.statusHealthy
}

@Composable
fun AdminHomeAiTab(
    modifier: Modifier = Modifier,
    viewModel: AdminHomeAiViewModel = hiltViewModel(),
) {
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val reachable by viewModel.reachable.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(CheradipScreenEdgePadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Home AI (Intel Arc)", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { viewModel.refresh() }, enabled = !loading) {
                Text(if (loading) "…" else "Refresh")
            }
        }
        Text(
            "Override HOME_AI_BASE_URL for testing. Release APK uses https://ai.cheradip.com",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            label = { Text("Home AI base URL") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            singleLine = true,
        )
        Button(
            onClick = viewModel::saveBaseUrl,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Save URL") }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        AssistChip(
            onClick = {},
            label = {
                Text(
                    when (reachable) {
                        true -> "Reachable ✓"
                        false -> "Unreachable"
                        null -> "Checking…"
                    },
                )
            },
            modifier = Modifier.padding(top = 12.dp),
        )
        status?.let { s ->
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backend: ${s.inferenceBackend}", style = MaterialTheme.typography.titleMedium)
                    Text("GPU: ${if (s.gpuAvailable) "available" else "unavailable"}")
                    Text("Model loaded: ${s.modelLoaded ?: "none (loads on first LLM request)"}")
                    Text("Queue depth: ${s.queueDepth}")
                    Text(
                        "Cache hit rate: ${s.cacheHitRatePct}% " +
                            "(L1 ${s.cacheHitRateL1}% · L2 ${s.cacheHitRateL2}% · L3 ${s.cacheHitRateL3}%)",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Routes: ${s.routesTotal} · Rate limit allowed: ${s.rateLimitAllowed} / rejected: ${s.rateLimitRejected}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (s.residentModels.isNotEmpty()) {
                        Text(
                            "Resident: ${s.residentModels.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
