package com.v2ray.ang.hardvpn

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.TrafficSample
import com.v2ray.ang.hardvpn.ads.HardVpnAds
import com.v2ray.ang.hardvpn.data.ConnectionSnapshot
import com.v2ray.ang.hardvpn.data.HardVpnEvent
import com.v2ray.ang.hardvpn.data.HardVpnRepository
import com.v2ray.ang.hardvpn.domain.AddSourceResult
import com.v2ray.ang.hardvpn.domain.AutoSelectPolicy
import com.v2ray.ang.hardvpn.domain.ConnectionPhase
import com.v2ray.ang.hardvpn.domain.DnsAddressValidation
import com.v2ray.ang.hardvpn.domain.DnsPreset
import com.v2ray.ang.hardvpn.domain.DnsProfile
import com.v2ray.ang.hardvpn.domain.DomainSnapshot
import com.v2ray.ang.hardvpn.domain.DomainUiState
import com.v2ray.ang.hardvpn.domain.HardVpnDomain
import com.v2ray.ang.hardvpn.domain.HardVpnTab
import com.v2ray.ang.hardvpn.domain.HardVpnUiState
import com.v2ray.ang.hardvpn.domain.UiMessage
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Owns all HardVPN state and every engine interaction.
 *
 * All blocking work (MMKV reads, subscription downloads, config parsing, DNS writes) runs on
 * [Dispatchers.IO]; the connection phase always comes from daemon broadcasts.
 */
class HardVpnViewModel(
    application: Application,
    private val repository: HardVpnRepository,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(HardVpnUiState())
    val uiState: StateFlow<HardVpnUiState> = _uiState.asStateFlow()

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    private val effectChannel = Channel<HardVpnEffect>(Channel.BUFFERED)
    val effects: Flow<HardVpnEffect> = effectChannel.receiveAsFlow()

    private var initialized = false
    private var foreground = false
    private var pendingConnectGuid: String? = null

    private var connectWatchdog: Job? = null
    private var disconnectWatchdog: Job? = null
    private var pingWatchdog: Job? = null
    private var trafficLoop: Job? = null
    private var autoSelectJob: Job? = null

    init {
        observeEngine()
        initialize()
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    // ---------- Actions ----------

    fun onAction(action: HardVpnAction) {
        when (action) {
            is HardVpnAction.SelectTab -> selectTab(action.tab)
            HardVpnAction.ToggleConnection -> toggleConnection()
            is HardVpnAction.SelectServer -> selectServer(action.domain, action.guid)
            is HardVpnAction.SelectAutoSelect -> selectAuto(action.domain)
            is HardVpnAction.RefreshDomain -> refreshDomain(action.domain)
            is HardVpnAction.RefreshSource -> refreshSource(action.domain, action.sourceId)
            is HardVpnAction.AddSource -> addSource(action.domain, action.name, action.url)
            is HardVpnAction.RenameSource ->
                renameSource(action.domain, action.sourceId, action.name)

            is HardVpnAction.DeleteSource -> deleteSource(action.domain, action.sourceId)
            is HardVpnAction.UpdateQuery -> updateQuery(action.domain, action.query)
            is HardVpnAction.UpdateManualQuery ->
                _uiState.update { it.copy(manual = it.manual.copy(query = action.query)) }
            is HardVpnAction.TestDomain -> testDomain(action.domain)
            HardVpnAction.TestManual -> testManual()
            is HardVpnAction.ImportText -> importText(action.text)
            HardVpnAction.RequestClipboardImport -> effectChannel.trySend(HardVpnEffect.ReadClipboard)
            HardVpnAction.RequestQrImport -> effectChannel.trySend(HardVpnEffect.OpenQrScanner)
            HardVpnAction.RequestFileImport -> effectChannel.trySend(HardVpnEffect.OpenFilePicker)
            is HardVpnAction.SelectManual -> selectManual(action.guid)
            is HardVpnAction.ConnectManual -> connectManual(action.guid)
            is HardVpnAction.RenameManual -> renameManual(action.guid, action.name)
            is HardVpnAction.DeleteManual -> deleteManual(action.guid)
            is HardVpnAction.EditManual -> effectChannel.trySend(
                HardVpnEffect.OpenManualEditor(action.guid, action.configTypeValue)
            )

            is HardVpnAction.AddManual -> effectChannel.trySend(
                HardVpnEffect.OpenManualEditor(guid = "", configTypeValue = action.configTypeValue)
            )

            HardVpnAction.OpenLegacyTools -> effectChannel.trySend(HardVpnEffect.OpenLegacyTools)
            is HardVpnAction.DnsApplyPreset -> applyDnsPreset(action.preset)
            is HardVpnAction.DnsApplyCustom -> applyDnsCustom(action.primary, action.secondary)
            is HardVpnAction.DnsSaveProfile -> saveDnsProfile(
                action.id, action.name, action.primary, action.secondary
            )

            is HardVpnAction.DnsApplyProfile -> applyDnsProfile(action.id)
            is HardVpnAction.DnsDeleteProfile -> deleteDnsProfile(action.id)
            HardVpnAction.RefreshDns -> viewModelScope.launch { reloadDns() }
            HardVpnAction.ToggleBoostSecurity -> toggleBoostSecurity()
        }
    }

    /** Called by the Activity with its own start/stop lifecycle to bound traffic polling. */
    fun onForegroundChanged(isForeground: Boolean) {
        foreground = isForeground
        if (isForeground) {
            if (repository.connection.value.phase == ConnectionPhase.CONNECTED) {
                startTrafficLoop()
            }
        } else {
            stopTrafficLoop()
        }
    }

    /** Result of the system VPN consent dialog, or true in proxy-only mode. */
    fun onVpnPermissionResult(granted: Boolean) {
        val guid = pendingConnectGuid
        pendingConnectGuid = null
        if (!granted) {
            emitMessage(R.string.hardvpn_error_vpn_permission)
            return
        }
        if (guid == null) return
        if (repository.connection.value.phase == ConnectionPhase.CONNECTED) {
            repository.restart(guid)
        } else {
            repository.start(guid)
        }
        startConnectWatchdog()
    }

    /** Re-reads Manual Import after the Activity returns from a v2rayNG profile editor. */
    fun onManualEditorResult() {
        viewModelScope.launch { reloadManual() }
    }

    // ---------- Engine observation ----------

    private fun observeEngine() {
        viewModelScope.launch {
            repository.connection.collect(::applyConnection)
        }
        viewModelScope.launch {
            repository.traffic.collect(::applyTraffic)
        }
        viewModelScope.launch {
            repository.events.collect(::handleEvent)
        }
    }

    private fun applyConnection(snapshot: ConnectionSnapshot) {
        val previous = _uiState.value.connection
        val newPhase = snapshot.phase
        _uiState.update { state ->
            var connection = state.connection.copy(
                phase = newPhase,
                activeGuid = snapshot.activeGuid,
                activeServerName = snapshot.activeServerName,
                connectedSinceMillis = snapshot.connectedSinceMillis,
                error = when (newPhase) {
                    ConnectionPhase.ERROR -> UiMessage(R.string.hardvpn_error_connection_failed)
                    ConnectionPhase.CONNECTED -> null
                    else -> state.connection.error
                },
            )
            if (newPhase == ConnectionPhase.CONNECTING && previous.phase != ConnectionPhase.CONNECTING) {
                // A new attempt starts a new session: no stale speed, totals, or timer.
                connection = connection.copy(
                    downloadBytesPerSecond = 0,
                    uploadBytesPerSecond = 0,
                    downloadedBytes = 0,
                    uploadedBytes = 0,
                    connectedSinceMillis = null,
                )
            }
            if (newPhase == ConnectionPhase.DISCONNECTED) {
                connection = connection.copy(
                    downloadBytesPerSecond = 0,
                    uploadBytesPerSecond = 0,
                    downloadedBytes = 0,
                    uploadedBytes = 0,
                    connectedSinceMillis = null,
                )
            }
            state.copy(
                connection = connection,
                dns = if (newPhase == ConnectionPhase.CONNECTED) {
                    state.dns.copy(requiresReconnect = false)
                } else {
                    state.dns
                },
                boost = if (newPhase == ConnectionPhase.CONNECTED) {
                    state.boost.copy(requiresReconnect = false)
                } else {
                    state.boost
                },
            )
        }

        if (newPhase != ConnectionPhase.CONNECTING) connectWatchdog?.cancel()
        if (newPhase != ConnectionPhase.DISCONNECTING) disconnectWatchdog?.cancel()
        if (newPhase == ConnectionPhase.CONNECTED) {
            HardVpnAds.gate.onConnectionEstablished()
            startTrafficLoop()
        } else {
            stopTrafficLoop()
        }
        if (newPhase == ConnectionPhase.CONNECTED && foreground) {
            repository.requestTrafficSample(force = true)
        }
    }

    private fun applyTraffic(sample: TrafficSample) {
        if (_uiState.value.connection.phase != ConnectionPhase.CONNECTED) return
        val seconds = sample.intervalMillis / 1000.0
        val downloadRate = if (seconds > 0.0) (sample.downlinkBytes / seconds).toLong() else 0L
        val uploadRate = if (seconds > 0.0) (sample.uplinkBytes / seconds).toLong() else 0L
        _uiState.update { state ->
            state.copy(
                connection = state.connection.copy(
                    downloadBytesPerSecond = downloadRate,
                    uploadBytesPerSecond = uploadRate,
                    downloadedBytes = state.connection.downloadedBytes + sample.downlinkBytes,
                    uploadedBytes = state.connection.uploadedBytes + sample.uplinkBytes,
                    connectedSinceMillis = sample.connectedAtMillis
                        .takeIf { it > 0L }
                        ?: state.connection.connectedSinceMillis,
                )
            )
        }
    }

    private fun handleEvent(event: HardVpnEvent) {
        when (event) {
            HardVpnEvent.StartFailure -> emitMessage(R.string.hardvpn_error_connection_failed)
            HardVpnEvent.StartSuccess -> Unit
            HardVpnEvent.Stopped -> Unit
            is HardVpnEvent.PingFinished -> {
                pingWatchdog?.cancel()
                clearTesting()
                viewModelScope.launch {
                    reloadDomain(HardVpnDomain.HOME)
                    reloadDomain(HardVpnDomain.EXPLORE)
                    reloadManual()
                }
            }
        }
    }

    private fun startTrafficLoop() {
        if (trafficLoop?.isActive == true) return
        trafficLoop = viewModelScope.launch {
            while (isActive) {
                if (foreground &&
                    repository.connection.value.phase == ConnectionPhase.CONNECTED
                ) {
                    repository.requestTrafficSample(force = true)
                }
                delay(TRAFFIC_INTERVAL_MS)
            }
        }
    }

    private fun stopTrafficLoop() {
        trafficLoop?.cancel()
        trafficLoop = null
    }

    // ---------- Connection ----------

    private fun toggleConnection() {
        val state = _uiState.value
        when (state.connection.phase) {
            ConnectionPhase.CONNECTING, ConnectionPhase.DISCONNECTING -> Unit // rapid-tap guard
            ConnectionPhase.CONNECTED -> {
                repository.stop()
                startDisconnectWatchdog()
            }

            ConnectionPhase.DISCONNECTED, ConnectionPhase.ERROR -> requestConnection()
        }
    }

    private fun requestConnection() {
        val state = _uiState.value
        if (state.tab == HardVpnTab.IMPORT_CONFIG) {
            val guid = state.manual.servers.firstOrNull { it.isSelected }?.guid
            if (guid == null) {
                emitMessage(R.string.hardvpn_error_no_manual_selection)
                return
            }
            beginConnect(guid)
            return
        }
        val domain = when (state.tab) {
            HardVpnTab.EXPLORE -> HardVpnDomain.EXPLORE
            else -> HardVpnDomain.HOME
        }
        val domainState = when (domain) {
            HardVpnDomain.HOME -> state.home
            HardVpnDomain.EXPLORE -> state.explore
        }
        if (!domainState.hasServers) {
            emitMessage(R.string.hardvpn_error_no_servers)
            return
        }
        val selected = domainState.selectedGuid
        if (domainState.autoSelect || selected == null) {
            startAutoSelect(domain)
        } else {
            beginConnect(selected)
        }
    }

    private fun beginConnect(guid: String) {
        pendingConnectGuid = guid
        // Synchronous no-op today; an ad implementation may never delay this path.
        HardVpnAds.gate.onConnectRequested()
        effectChannel.trySend(HardVpnEffect.RequestVpnPermission)
    }

    private fun connectManual(guid: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.selectManualServer(guid) }
            reloadManual()
            beginConnect(guid)
        }
    }

    private fun startConnectWatchdog() {
        connectWatchdog?.cancel()
        connectWatchdog = viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (repository.connection.value.phase == ConnectionPhase.CONNECTING) {
                LogUtil.e(AppConfig.TAG, "HardVPN: start attempt was not confirmed, aborting")
                repository.abortAttempt()
            }
        }
    }

    private fun startDisconnectWatchdog() {
        disconnectWatchdog?.cancel()
        disconnectWatchdog = viewModelScope.launch {
            delay(DISCONNECT_TIMEOUT_MS)
            if (repository.connection.value.phase == ConnectionPhase.DISCONNECTING) {
                repository.reconcileAfterUnconfirmedStop()
                emitMessage(R.string.hardvpn_error_disconnect_unconfirmed)
            }
        }
    }

    // ---------- Auto Select ----------

    private fun startAutoSelect(domain: HardVpnDomain) {
        if (autoSelectJob?.isActive == true) return
        val domainState = domainState(domain)
        val candidates = AutoSelectPolicy.candidateGuids(domainState.servers)
        if (candidates.isEmpty()) {
            emitMessage(R.string.hardvpn_error_no_servers)
            return
        }
        val requestId = UUID.randomUUID().toString()
        updateDomain(domain) { it.copy(isTesting = true) }
        if (!repository.startPing(candidates, onlyTcp = false, requestId = requestId)) {
            updateDomain(domain) { it.copy(isTesting = false) }
            emitMessage(R.string.hardvpn_error_no_servers)
            return
        }
        startPingWatchdog()
        autoSelectJob = viewModelScope.launch {
            val finished = withTimeoutOrNull(AUTO_SELECT_TIMEOUT_MS) {
                repository.ping.first { it.requestId == requestId && !it.running }
            }
            if (finished == null) {
                // Stop the probe service so a stalled run cannot keep working in the background.
                repository.cancelPing()
            }
            val measured = repository.ping.value.results.filterKeys { it in candidates }
            val cached = domainState.servers
                .filter { it.guid in candidates && it.pingMillis != null }
                .associate { it.guid to (it.pingMillis ?: -1L) }
            val best = AutoSelectPolicy.bestServer(measured, cached)
            updateDomain(domain) { it.copy(isTesting = false) }
            if (best == null) {
                emitMessage(R.string.hardvpn_error_no_reachable_server)
                return@launch
            }
            withContext(Dispatchers.IO) { repository.applyAutoSelection(domain, best) }
            reloadDomain(domain)
            beginConnect(best)
        }
    }

    private fun selectAuto(domain: HardVpnDomain) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.useAutoSelect(domain) }
            reloadDomain(domain)
        }
    }

    // ---------- Selection ----------

    private fun selectServer(domain: HardVpnDomain, guid: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.selectDomainServer(domain, guid, autoSelect = false)
            }
            reloadDomain(domain)
            applySelectionToLiveTunnel(guid)
        }
    }

    private fun selectManual(guid: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.selectManualServer(guid) }
            reloadManual()
            applySelectionToLiveTunnel(guid)
        }
    }

    /** A live tunnel follows a new selection through the existing restart path, nothing else. */
    private fun applySelectionToLiveTunnel(guid: String) {
        if (repository.connection.value.phase != ConnectionPhase.CONNECTED) return
        repository.restart(guid)
        startConnectWatchdog()
    }

    // ---------- Domains ----------

    private fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            reloadDomain(HardVpnDomain.HOME)
            reloadDomain(HardVpnDomain.EXPLORE)
        }
        viewModelScope.launch { reloadManual() }
        viewModelScope.launch { reloadDns() }
    }

    private fun selectTab(tab: HardVpnTab) {
        _uiState.update { it.copy(tab = tab) }
        when (tab) {
            HardVpnTab.HOME -> viewModelScope.launch { reloadDomain(HardVpnDomain.HOME) }
            HardVpnTab.EXPLORE -> viewModelScope.launch { reloadDomain(HardVpnDomain.EXPLORE) }
            HardVpnTab.IMPORT_CONFIG -> viewModelScope.launch { reloadManual() }
            HardVpnTab.DNS_GAMING -> viewModelScope.launch { reloadDns() }
        }
    }

    private suspend fun reloadDomain(domain: HardVpnDomain) {
        val snapshot = withContext(Dispatchers.IO) { repository.loadDomain(domain) }
        applySnapshot(domain, snapshot)
    }

    private fun applySnapshot(domain: HardVpnDomain, snapshot: DomainSnapshot) {
        updateDomain(domain) { current ->
            current.copy(
                isLoading = false,
                servers = snapshot.servers,
                sources = snapshot.sources,
                selectedGuid = snapshot.selectedGuid,
                autoSelect = snapshot.autoSelect,
            )
        }
    }

    private suspend fun reloadManual() {
        val servers = withContext(Dispatchers.IO) { repository.loadManualServers() }
        _uiState.update { state ->
            state.copy(manual = state.manual.copy(isLoading = false, servers = servers))
        }
    }

    private fun refreshDomain(domain: HardVpnDomain) {
        val domainState = domainState(domain)
        if (domainState.isRefreshing) return
        if (domainState.sources.isEmpty()) {
            emitMessage(R.string.hardvpn_error_no_sources)
            return
        }
        updateDomain(domain) { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.refreshDomain(domain) }
            reloadDomain(domain)
            updateDomain(domain) { it.copy(isRefreshing = false) }
            when {
                outcome.hasSuccess && !outcome.hasFailure ->
                    emitMessage(UiMessage(R.string.hardvpn_refresh_success, listOf(outcome.configCount)))

                outcome.hasFailure && !outcome.hasSuccess ->
                    emitMessage(R.string.hardvpn_refresh_failed_kept_data)

                outcome.hasFailure ->
                    emitMessage(R.string.hardvpn_refresh_partial)

                else -> emitMessage(R.string.hardvpn_error_no_sources)
            }
        }
    }

    private fun refreshSource(domain: HardVpnDomain, sourceId: String) {
        updateDomain(domain) { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.refreshSource(sourceId) }
            reloadDomain(domain)
            updateDomain(domain) { it.copy(isRefreshing = false) }
            if (outcome.hasSuccess) {
                emitMessage(UiMessage(R.string.hardvpn_refresh_success, listOf(outcome.configCount)))
            } else {
                emitMessage(R.string.hardvpn_refresh_failed_kept_data)
            }
        }
    }

    private fun addSource(domain: HardVpnDomain, name: String, url: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.addSource(domain, name, url) }
            reloadDomain(domain)
            when (result) {
                is AddSourceResult.Success ->
                    emitMessage(UiMessage(R.string.hardvpn_source_added, listOf(result.configCount)))

                AddSourceResult.Empty -> emitMessage(R.string.hardvpn_source_empty)
                AddSourceResult.InvalidUrl -> emitMessage(R.string.hardvpn_source_invalid_url)
                AddSourceResult.Failed -> emitMessage(R.string.hardvpn_source_add_failed)
            }
        }
    }

    private fun renameSource(domain: HardVpnDomain, sourceId: String, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.updateSourceName(sourceId, name) }
            reloadDomain(domain)
        }
    }

    private fun deleteSource(domain: HardVpnDomain, sourceId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteSource(domain, sourceId) }
            reloadDomain(domain)
            emitMessage(R.string.hardvpn_source_deleted)
        }
    }

    private fun updateQuery(domain: HardVpnDomain, query: String) {
        updateDomain(domain) { it.copy(query = query) }
    }

    private fun testDomain(domain: HardVpnDomain) {
        viewModelScope.launch {
            val guids = withContext(Dispatchers.IO) { repository.domainGuids(domain) }
            if (guids.isEmpty()) {
                emitMessage(R.string.hardvpn_error_no_servers)
                return@launch
            }
            updateDomain(domain) { it.copy(isTesting = true) }
            if (!repository.startPing(guids, onlyTcp = false, requestId = UUID.randomUUID().toString())) {
                updateDomain(domain) { it.copy(isTesting = false) }
                emitMessage(R.string.hardvpn_error_no_servers)
                return@launch
            }
            startPingWatchdog()
        }
    }

    private fun testManual() {
        viewModelScope.launch {
            val guids = withContext(Dispatchers.IO) { repository.pingAllManual() }
            if (guids.isEmpty()) {
                emitMessage(R.string.hardvpn_error_no_servers)
                return@launch
            }
            _uiState.update { it.copy(manual = it.manual.copy(isTesting = true)) }
            repository.startPing(guids, onlyTcp = false, requestId = UUID.randomUUID().toString())
            startPingWatchdog()
        }
    }

    private fun startPingWatchdog() {
        pingWatchdog?.cancel()
        pingWatchdog = viewModelScope.launch {
            delay(PING_TIMEOUT_MS)
            if (repository.ping.value.running) {
                repository.cancelPing()
                clearTesting()
            }
        }
    }

    private fun clearTesting() {
        _uiState.update { state ->
            state.copy(
                home = state.home.copy(isTesting = false),
                explore = state.explore.copy(isTesting = false),
                manual = state.manual.copy(isTesting = false),
            )
        }
    }

    // ---------- Manual import ----------

    private fun importText(text: String) {
        if (text.isBlank()) {
            emitMessage(R.string.hardvpn_import_failed)
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.importManual(text) }
            when {
                result.sourceUrlRejected ->
                    emitMessage(R.string.hardvpn_import_source_url_rejected)

                result.count > 0 -> {
                    _uiState.update { it.copy(tab = HardVpnTab.IMPORT_CONFIG) }
                    reloadManual()
                    emitMessage(UiMessage(R.string.hardvpn_import_success, listOf(result.count)))
                }

                else -> emitMessage(R.string.hardvpn_import_failed)
            }
        }
    }

    private fun renameManual(guid: String, name: String) {
        viewModelScope.launch {
            val renamed = withContext(Dispatchers.IO) { repository.renameManualServer(guid, name) }
            reloadManual()
            emitMessage(
                if (renamed) R.string.hardvpn_manual_renamed
                else R.string.hardvpn_manual_rename_failed
            )
        }
    }

    private fun deleteManual(guid: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteManualServer(guid) }
            reloadManual()
            emitMessage(R.string.hardvpn_manual_deleted)
        }
    }

    // ---------- DNS Gaming ----------

    private suspend fun reloadDns() {
        val applied = withContext(Dispatchers.IO) { repository.appliedDns() }
        val profiles = withContext(Dispatchers.IO) { repository.dnsProfiles() }
        val active = withContext(Dispatchers.IO) { repository.isBoostSecurityActive() }
        _uiState.update { state ->
            val preset = when {
                applied.first == CLOUDFLARE_PRIMARY && applied.second == CLOUDFLARE_SECONDARY ->
                    DnsPreset.CLOUDFLARE

                else -> state.dns.preset
            }
            state.copy(
                dns = state.dns.copy(
                    appliedPrimary = applied.first,
                    appliedSecondary = applied.second,
                    profiles = profiles,
                    preset = preset,
                    customPrimary = state.dns.customPrimary.ifBlank { applied.first.orEmpty() },
                    customSecondary = state.dns.customSecondary.ifBlank { applied.second.orEmpty() },
                ),
                boost = state.boost.copy(active = active),
            )
        }
    }

    private fun applyDnsPreset(preset: DnsPreset) {
        when (preset) {
            DnsPreset.CLOUDFLARE -> applyDns(CLOUDFLARE_PRIMARY, CLOUDFLARE_SECONDARY, preset)
            DnsPreset.ELECTRO -> {
                // No verified Electro DNS address exists in this repository; never invented.
                emitMessage(R.string.hardvpn_dns_electro_unavailable)
            }

            DnsPreset.CUSTOM -> applyDnsCustom(
                _uiState.value.dns.customPrimary,
                _uiState.value.dns.customSecondary,
            )
        }
    }

    private fun applyDnsCustom(primary: String, secondary: String) {
        applyDns(primary.trim(), secondary.trim(), DnsPreset.CUSTOM)
    }

    private fun applyDns(primary: String, secondary: String, preset: DnsPreset) {
        if (!DnsAddressValidation.isValidLiteral(primary)) {
            emitMessage(R.string.hardvpn_dns_invalid_address)
            return
        }
        if (secondary.isNotBlank() && !DnsAddressValidation.isValidLiteral(secondary)) {
            emitMessage(R.string.hardvpn_dns_invalid_address)
            return
        }
        viewModelScope.launch {
            val applied = withContext(Dispatchers.IO) {
                repository.applyVpnDns(primary, secondary.ifBlank { null })
            }
            if (!applied) {
                emitMessage(R.string.hardvpn_dns_apply_failed)
                return@launch
            }
            val liveTunnel = repository.connection.value.phase == ConnectionPhase.CONNECTED
            _uiState.update { state ->
                state.copy(
                    dns = state.dns.copy(
                        preset = preset,
                        customPrimary = primary,
                        customSecondary = secondary,
                        requiresReconnect = liveTunnel,
                        appliedPrimary = primary,
                        appliedSecondary = secondary.ifBlank { null },
                    )
                )
            }
            if (liveTunnel) {
                repository.connection.value.activeGuid?.let {
                    repository.restart(it)
                    startConnectWatchdog()
                }
            }
            emitMessage(R.string.hardvpn_dns_applied)
            reloadDns()
        }
    }

    private fun saveDnsProfile(id: String?, name: String, primary: String, secondary: String) {
        if (name.isBlank() || !DnsAddressValidation.isValidLiteral(primary)) {
            emitMessage(R.string.hardvpn_dns_profile_invalid)
            return
        }
        if (secondary.isNotBlank() && !DnsAddressValidation.isValidLiteral(secondary)) {
            emitMessage(R.string.hardvpn_dns_profile_invalid)
            return
        }
        viewModelScope.launch {
            val profile = DnsProfile(
                id = id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                primary = primary.trim(),
                secondary = secondary.trim().ifBlank { null },
            )
            withContext(Dispatchers.IO) { repository.saveDnsProfile(profile) }
            reloadDns()
            emitMessage(R.string.hardvpn_dns_profile_saved)
        }
    }

    private fun applyDnsProfile(id: String) {
        val profile = _uiState.value.dns.profiles.firstOrNull { it.id == id } ?: return
        applyDns(profile.primary, profile.secondary.orEmpty(), DnsPreset.CUSTOM)
    }

    private fun deleteDnsProfile(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteDnsProfile(id) }
            reloadDns()
        }
    }

    // ---------- Boost Security ----------

    private fun toggleBoostSecurity() {
        val currentlyActive = _uiState.value.boost.active
        viewModelScope.launch {
            val applied = withContext(Dispatchers.IO) {
                repository.setBoostSecurityEnabled(!currentlyActive)
            }
            val isActive = withContext(Dispatchers.IO) { repository.isBoostSecurityActive() }
            if (!applied && isActive == currentlyActive) {
                emitMessage(R.string.hardvpn_boost_apply_failed)
                return@launch
            }
            val liveTunnel = repository.connection.value.phase == ConnectionPhase.CONNECTED
            _uiState.update {
                it.copy(boost = it.boost.copy(active = isActive, requiresReconnect = liveTunnel))
            }
            if (liveTunnel) {
                repository.connection.value.activeGuid?.let {
                    repository.restart(it)
                    startConnectWatchdog()
                }
            }
            emitMessage(
                if (isActive) R.string.hardvpn_boost_enabled else R.string.hardvpn_boost_disabled
            )
            reloadDns()
        }
    }

    // ---------- Helpers ----------

    private fun domainState(domain: HardVpnDomain): DomainUiState =
        when (domain) {
            HardVpnDomain.HOME -> _uiState.value.home
            HardVpnDomain.EXPLORE -> _uiState.value.explore
        }

    private fun updateDomain(domain: HardVpnDomain, transform: (DomainUiState) -> DomainUiState) {
        _uiState.update { state ->
            when (domain) {
                HardVpnDomain.HOME -> state.copy(home = transform(state.home))
                HardVpnDomain.EXPLORE -> state.copy(explore = transform(state.explore))
            }
        }
    }

    private fun emitMessage(textRes: Int) {
        messageChannel.trySend(UiMessage(textRes))
    }

    private fun emitMessage(message: UiMessage) {
        messageChannel.trySend(message)
    }

    class Factory(
        private val application: Application,
        private val repository: HardVpnRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HardVpnViewModel::class.java)) {
                return HardVpnViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private companion object {
        const val CLOUDFLARE_PRIMARY = "1.1.1.1"
        const val CLOUDFLARE_SECONDARY = "1.0.0.1"
        const val TRAFFIC_INTERVAL_MS = 2_000L
        const val CONNECT_TIMEOUT_MS = 45_000L
        const val DISCONNECT_TIMEOUT_MS = 8_000L
        const val PING_TIMEOUT_MS = 180_000L
        const val AUTO_SELECT_TIMEOUT_MS = 25_000L
    }
}
