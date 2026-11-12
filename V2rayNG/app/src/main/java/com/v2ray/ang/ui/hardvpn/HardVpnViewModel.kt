package com.v2ray.ang.ui.hardvpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.ui.hardvpn.data.HardVpnEngine
import com.v2ray.ang.ui.hardvpn.data.HardVpnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

data class UiBanner(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val isError: Boolean = false,
)

data class HardVpnUiState(
    val destination: HardVpnDestination = HardVpnDestination.Home,
    val phase: ConnectionPhase = ConnectionPhase.Disconnected,
    val statusMessage: String? = null,
    val sessionStartedAt: Long = 0L,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val totalDownload: Long = 0L,
    val totalUpload: Long = 0L,
    val home: DomainSnapshot = DomainSnapshot(),
    val explore: DomainSnapshot = DomainSnapshot(),
    val manualServers: List<SafeServer> = emptyList(),
    val selectedHome: String? = null,
    val selectedExplore: String? = null,
    val selectedManual: String? = null,
    val autoSelect: Boolean = false,
    val activeDomain: ConfigDomain = ConfigDomain.Home,
    val activeServerName: String = "",
    val activeServerCountryCode: String? = null,
    val activeServerLocation: String = "",
    val activeServerPing: Long? = null,
    val activeSourceLabel: String = "",
    val boostSecurityEnabled: Boolean = false,
    val homeLoading: Boolean = false,
    val exploreLoading: Boolean = false,
    val manualLoading: Boolean = false,
    val refreshing: Boolean = false,
    val importing: Boolean = false,
    val pingRunning: Boolean = false,
    val banner: UiBanner? = null,
    val dns: DnsUiState = DnsUiState(),
) {
    val isConnected: Boolean get() = phase == ConnectionPhase.Connected
    val isBusy: Boolean get() = phase == ConnectionPhase.Connecting ||
            phase == ConnectionPhase.Disconnecting
}

/**
 * Single owner of HardVPN screen state.
 *
 * Every connection value is derived from a daemon broadcast, every traffic value from the
 * daemon's own counters, and every server list from the profile storage of one domain only.
 * Nothing here simulates a connection or invents a ping.
 */
class HardVpnViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AngApplication
    val repository = HardVpnRepository(app)
    private val engine = HardVpnEngine(app)

    private val _uiState = MutableStateFlow(HardVpnUiState())
    val uiState: StateFlow<HardVpnUiState> = _uiState.asStateFlow()

    private val connectGuard = AtomicBoolean(false)
    private var connectTimeoutJob: Job? = null
    private var lastTrafficRequestAt = 0L
    private var lastTrafficUplink = 0L
    private var lastTrafficDownlink = 0L
    private var lastTrafficSampleAt = 0L
    private var pendingDomain: ConfigDomain = ConfigDomain.Home
    private var initialized = false

    init {
        collectEngineEvents()
        refreshAll()
    }

    // ------------------------------------------------------------------
    // Engine events
    // ------------------------------------------------------------------

    private fun collectEngineEvents() {
        viewModelScope.launch {
            engine.engineEvents.collect { event ->
                when (event) {
                    HardVpnEngine.EngineEvent.Running -> onEngineRunning()
                    HardVpnEngine.EngineEvent.NotRunning -> onEngineStopped()
                    HardVpnEngine.EngineEvent.StartSuccess -> onEngineStarted()
                    is HardVpnEngine.EngineEvent.StartFailure -> onEngineFailure(event.message)
                    HardVpnEngine.EngineEvent.StopSuccess -> onEngineStopped()
                    is HardVpnEngine.EngineEvent.Traffic -> onTrafficSample(
                        event.uplinkTotal,
                        event.downlinkTotal
                    )

                    is HardVpnEngine.EngineEvent.PingProgress -> Unit
                    HardVpnEngine.EngineEvent.PingFinished -> {
                        _uiState.value = _uiState.value.copy(pingRunning = false)
                        refreshAll()
                    }

                    HardVpnEngine.EngineEvent.PingCancelled -> {
                        _uiState.value = _uiState.value.copy(pingRunning = false)
                    }
                }
            }
        }
    }

    private fun onEngineRunning() {
        val startedAt = engine.sessionStartedAtMillis()
        _uiState.value = _uiState.value.copy(
            phase = ConnectionPhase.Connected,
            statusMessage = null,
            sessionStartedAt = startedAt,
        )
        if (startedAt <= 0L) {
            // The tunnel predates this app process; no honest start time is available.
            _uiState.value = _uiState.value.copy(sessionStartedAt = 0L)
        }
        resetTrafficBaseline()
        engine.requestTraffic()
    }

    private fun onEngineStarted() {
        connectTimeoutJob?.cancel()
        connectGuard.set(false)
        val startedAt = engine.sessionStartedAtMillis()
        _uiState.value = _uiState.value.copy(
            phase = ConnectionPhase.Connected,
            statusMessage = null,
            sessionStartedAt = startedAt,
        )
        resetTrafficBaseline()
        engine.requestTraffic()
    }

    private fun onEngineStopped() {
        connectTimeoutJob?.cancel()
        connectGuard.set(false)
        lastTrafficUplink = 0L
        lastTrafficDownlink = 0L
        lastTrafficSampleAt = 0L
        _uiState.value = _uiState.value.copy(
            phase = ConnectionPhase.Disconnected,
            statusMessage = null,
            sessionStartedAt = 0L,
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            totalDownload = 0L,
            totalUpload = 0L,
        )
    }

    private fun onEngineFailure(rawMessage: String?) {
        connectTimeoutJob?.cancel()
        connectGuard.set(false)
        _uiState.value = _uiState.value.copy(
            phase = ConnectionPhase.Error,
            statusMessage = sanitizeEngineMessage(rawMessage),
            sessionStartedAt = 0L,
        )
    }

    /**
     * Core error strings can mention the server address or port. Only a short, sanitised
     * explanation ever reaches the UI.
     */
    private fun sanitizeEngineMessage(raw: String?): String {
        val text = raw.orEmpty().trim()
        if (text.isEmpty()) return "The VPN service could not start"
        val cleaned = text
            .replace(Regex("(?i)[a-z][a-z0-9+.-]*://\\S+"), " ")
            .replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), " ")
            .replace(Regex("(?i)\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(120).ifEmpty { "The VPN service could not start" }
    }

    // ------------------------------------------------------------------
    // Connection lifecycle
    // ------------------------------------------------------------------

    /**
     * Resolves which server the engine should use and validates that one exists.
     * Returns false when there is nothing to connect to; the UI then shows the reason.
     */
    suspend fun prepareConnection(domain: ConfigDomain): Boolean {
        if (connectGuard.get() || _uiState.value.isBusy || _uiState.value.isConnected) return false
        connectGuard.set(true)
        pendingDomain = domain

        val chosen: SafeServer? = withContext(Dispatchers.IO) {
            if (domain == ConfigDomain.Home && repository.isAutoSelect(ConfigDomain.Home)) {
                resolveAutoServer()
            } else {
                val selected = repository.selectedGuid(domain)
                if (selected.isNullOrBlank()) {
                    null
                } else {
                    repository.load(domain).servers.firstOrNull { it.guid == selected }
                        ?: if (domain == ConfigDomain.Manual) {
                            repository.loadManual().firstOrNull { it.guid == selected }
                        } else null
                }
            }
        }

        if (chosen == null) {
            connectGuard.set(false)
            showBanner(
                when {
                    domain == ConfigDomain.Manual -> "Select a manual configuration first"
                    domain == ConfigDomain.Explore -> "Select an ExploreVPN server first"
                    repository.isAutoSelect(ConfigDomain.Home) ->
                        "No Home server answered the latency test. Add or refresh a source, then retry."

                    else -> "Select a Home server first"
                },
                isError = true
            )
            return false
        }

        repository.selectServer(chosen.guid, domain)
        refreshActiveServer()
        return true
    }

    /** Called after VpnService permission was granted (or when no permission is needed). */
    fun startEngine() {
        if (_uiState.value.isConnected) {
            connectGuard.set(false)
            return
        }
        _uiState.value = _uiState.value.copy(
            phase = ConnectionPhase.Connecting,
            statusMessage = null,
        )
        engine.start()
        connectTimeoutJob?.cancel()
        connectTimeoutJob = viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_uiState.value.phase == ConnectionPhase.Connecting) {
                // The daemon never confirmed the tunnel: report it instead of faking success.
                _uiState.value = _uiState.value.copy(
                    phase = ConnectionPhase.Error,
                    statusMessage = "No response from the VPN service. Try again.",
                )
                connectGuard.set(false)
            }
        }
    }

    fun cancelPendingConnection() {
        connectGuard.set(false)
        if (_uiState.value.phase == ConnectionPhase.Connecting) {
            _uiState.value = _uiState.value.copy(
                phase = ConnectionPhase.Disconnected,
                statusMessage = null,
            )
        }
    }

    fun disconnect() {
        if (_uiState.value.phase == ConnectionPhase.Disconnecting) return
        _uiState.value = _uiState.value.copy(phase = ConnectionPhase.Disconnecting)
        engine.stop()
    }

    fun clearError() {
        if (_uiState.value.phase == ConnectionPhase.Error) {
            _uiState.value = _uiState.value.copy(
                phase = ConnectionPhase.Disconnected,
                statusMessage = null,
            )
        }
    }

    fun isVpnMode(): Boolean = engine.isVpnMode()

    /**
     * Bounded auto-select: cached measured results first, then one small real-ping batch.
     * Never pings every server and never invents a latency value.
     */
    private suspend fun resolveAutoServer(): SafeServer? {
        repository.fastestMeasured(ConfigDomain.Home)?.let { return it }

        val candidates = repository.eligibleServers(ConfigDomain.Home)
        if (candidates.isEmpty()) return null

        candidates.groupBy { it.sourceId }
            .entries
            .take(MAX_AUTO_PING_SOURCES)
            .forEach { (sourceId, servers) ->
                engine.startPingBatch(sourceId, servers.take(MAX_AUTO_PING_SERVERS).map { it.guid })
            }
        _uiState.value = _uiState.value.copy(pingRunning = true)

        var waited = 0L
        while (waited < AUTO_PING_TIMEOUT_MS && viewModelScope.isActive) {
            delay(AUTO_PING_POLL_MS)
            waited += AUTO_PING_POLL_MS
            repository.fastestMeasured(ConfigDomain.Home)?.let { best ->
                engine.cancelPing()
                return best
            }
        }
        engine.cancelPing()
        return repository.fastestMeasured(ConfigDomain.Home)
    }

    // ------------------------------------------------------------------
    // Traffic
    // ------------------------------------------------------------------

    private fun resetTrafficBaseline() {
        lastTrafficUplink = 0L
        lastTrafficDownlink = 0L
        lastTrafficSampleAt = 0L
        lastTrafficRequestAt = 0L
    }

    /** Requests one traffic sample; throttled so the daemon is never flooded. */
    fun sampleTraffic() {
        if (_uiState.value.phase != ConnectionPhase.Connected) return
        val now = System.currentTimeMillis()
        if (now - lastTrafficRequestAt < TRAFFIC_SAMPLE_INTERVAL_MS) return
        lastTrafficRequestAt = now
        engine.requestTraffic()
    }

    /** Re-issues a traffic sample immediately, used right after a connect. */
    fun forceTrafficSample() {
        lastTrafficRequestAt = 0L
        sampleTraffic()
    }

    private fun onTrafficSample(uplink: Long, downlink: Long) {
        val now = System.currentTimeMillis()
        if (_uiState.value.phase != ConnectionPhase.Connected) {
            lastTrafficUplink = uplink
            lastTrafficDownlink = downlink
            lastTrafficSampleAt = now
            return
        }
        if (uplink < lastTrafficUplink || downlink < lastTrafficDownlink) {
            // The daemon restarted its counters; start a fresh baseline instead of a negative rate.
            lastTrafficUplink = uplink
            lastTrafficDownlink = downlink
            lastTrafficSampleAt = now
            _uiState.value = _uiState.value.copy(
                totalUpload = uplink,
                totalDownload = downlink,
                uploadSpeed = 0L,
                downloadSpeed = 0L,
            )
            return
        }
        val elapsed = max(now - lastTrafficSampleAt, 250L)
        val upSpeed = (uplink - lastTrafficUplink) * 1000L / elapsed
        val downSpeed = (downlink - lastTrafficDownlink) * 1000L / elapsed
        lastTrafficUplink = uplink
        lastTrafficDownlink = downlink
        lastTrafficSampleAt = now
        _uiState.value = _uiState.value.copy(
            totalUpload = uplink,
            totalDownload = downlink,
            uploadSpeed = upSpeed,
            downloadSpeed = downSpeed,
        )
    }

    // ------------------------------------------------------------------
    // Data loading
    // ------------------------------------------------------------------

    fun refreshAll() {
        loadDomain(ConfigDomain.Home)
        loadDomain(ConfigDomain.Explore)
        loadManual()
        refreshActiveServer()
        _uiState.value = _uiState.value.copy(
            boostSecurityEnabled = repository.isLocalDnsEnabled(),
            selectedHome = repository.selectedGuid(ConfigDomain.Home),
            selectedExplore = repository.selectedGuid(ConfigDomain.Explore),
            selectedManual = repository.selectedGuid(ConfigDomain.Manual),
            autoSelect = repository.isAutoSelect(ConfigDomain.Home),
            activeDomain = repository.activeDomain(),
        )
        if (!initialized) {
            initialized = true
            refreshDns()
        }
    }

    private fun loadDomain(domain: ConfigDomain) {
        _uiState.value = when (domain) {
            ConfigDomain.Home -> _uiState.value.copy(homeLoading = true)
            ConfigDomain.Explore -> _uiState.value.copy(exploreLoading = true)
            ConfigDomain.Manual -> _uiState.value
        }
        viewModelScope.launch {
            val snapshot = repository.load(domain)
            _uiState.value = when (domain) {
                ConfigDomain.Home -> _uiState.value.copy(home = snapshot, homeLoading = false)
                ConfigDomain.Explore -> _uiState.value.copy(explore = snapshot, exploreLoading = false)
                ConfigDomain.Manual -> _uiState.value
            }
        }
    }

    private fun loadManual() {
        _uiState.value = _uiState.value.copy(manualLoading = true)
        viewModelScope.launch {
            val servers = repository.loadManual()
            _uiState.value = _uiState.value.copy(
                manualServers = servers,
                manualLoading = false,
                selectedManual = repository.selectedGuid(ConfigDomain.Manual),
            )
        }
    }

    private fun refreshActiveServer() {
        val guid = engine.selectedGuid()
        val domain = repository.activeDomain()
        val ping = guid?.let { repository.persistedPing(it) }
        _uiState.value = _uiState.value.copy(
            activeDomain = domain,
            activeServerName = repository.activeServerLabel(),
            activeServerCountryCode = repository.activeServerCountryCode(),
            activeServerLocation = countryDisplayName(repository.activeServerCountryCode()).orEmpty(),
            activeServerPing = ping,
            activeSourceLabel = when (domain) {
                ConfigDomain.Home -> "Home VPN"
                ConfigDomain.Explore -> "ExploreVPN"
                ConfigDomain.Manual -> "Manual import"
            },
        )
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    fun selectServer(domain: ConfigDomain, guid: String) {
        repository.selectServer(guid, domain)
        if (domain == ConfigDomain.Home) {
            repository.setAutoSelect(false)
        }
        _uiState.value = _uiState.value.copy(
            selectedHome = repository.selectedGuid(ConfigDomain.Home),
            selectedExplore = repository.selectedGuid(ConfigDomain.Explore),
            selectedManual = repository.selectedGuid(ConfigDomain.Manual),
            autoSelect = repository.isAutoSelect(ConfigDomain.Home),
        )
        refreshActiveServer()
        if (_uiState.value.isConnected) {
            // The running tunnel keeps its old configuration until the daemon restarts.
            engine.restartIfRunning()
            resetTrafficBaseline()
            showBanner("Reconnecting with the selected server")
        }
    }

    fun setAutoSelect(enabled: Boolean) {
        repository.setAutoSelect(enabled)
        _uiState.value = _uiState.value.copy(autoSelect = enabled)
    }

    fun selectAutoMode() {
        repository.setAutoSelect(true)
        _uiState.value = _uiState.value.copy(autoSelect = true)
        refreshActiveServer()
    }

    // ------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------

    fun refreshDomain(domain: ConfigDomain) {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(refreshing = true)
            val report = repository.refresh(domain)
            _uiState.value = _uiState.value.copy(refreshing = false)
            refreshAll()
            showBanner(
                when {
                    report.nothingConfigured ->
                        if (domain == ConfigDomain.Explore) {
                            "No ExploreVPN source is configured yet"
                        } else {
                            "No Home subscription is configured yet"
                        }

                    report.updatedSources == 0 && report.failedSources > 0 ->
                        "Refresh failed. Previously imported servers were kept."

                    report.failedSources > 0 ->
                        "${report.updatedSources} source(s) updated, ${report.failedSources} failed. Existing servers were kept."

                    else -> "${report.updatedSources} source(s) updated"
                },
                isError = report.nothingConfigured || (report.updatedSources == 0 && report.failedSources > 0),
            )
        }
    }

    /** Refreshes one source of [domain]; other sources keep their last-known-good data. */
    fun refreshSource(domain: ConfigDomain, sourceId: String) {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(refreshing = true)
            val report = repository.refresh(domain, sourceId)
            _uiState.value = _uiState.value.copy(refreshing = false)
            refreshAll()
            showBanner(
                when {
                    report.updatedSources == 0 && report.failedSources > 0 ->
                        "Refresh failed. Previously imported servers were kept."

                    report.failedSources > 0 ->
                        "${report.updatedSources} source(s) updated, ${report.failedSources} failed. Existing servers were kept."

                    else -> "${report.updatedSources} source(s) updated"
                },
                isError = report.updatedSources == 0 && report.failedSources > 0,
            )
        }
    }

    fun addSource(domain: ConfigDomain, name: String, url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(refreshing = true)
            when (val result = repository.addSource(domain, name, url)) {
                is HardVpnRepository.AddSourceResult.Rejected -> {
                    _uiState.value = _uiState.value.copy(refreshing = false)
                    showBanner(result.reason, isError = true)
                }

                is HardVpnRepository.AddSourceResult.Added -> {
                    _uiState.value = _uiState.value.copy(refreshing = false)
                    refreshAll()
                    val count = repository.load(domain).sources
                        .firstOrNull { it.id == result.sourceId }?.serverCount ?: 0
                    showBanner(
                        if (count > 0) {
                            "Source added with $count configuration(s)"
                        } else {
                            "Source saved, but it returned no usable configuration"
                        },
                        isError = count == 0,
                    )
                }
            }
        }
    }

    fun renameSource(domain: ConfigDomain, sourceId: String, newName: String) {
        repository.renameSource(domain, sourceId, newName)
        refreshAll()
    }

    fun removeSource(domain: ConfigDomain, sourceId: String) {
        repository.removeSource(domain, sourceId)
        refreshAll()
        showBanner("Source removed")
    }

    // ------------------------------------------------------------------
    // Ping
    // ------------------------------------------------------------------

    fun pingDomain(domain: ConfigDomain) {
        if (_uiState.value.pingRunning) return
        viewModelScope.launch {
            val snapshot = repository.load(domain)
            if (snapshot.servers.isEmpty()) {
                showBanner("Nothing to test yet", isError = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(pingRunning = true)
            snapshot.sources.take(MAX_PING_SOURCES).forEach { source ->
                val guids = repository.guidsOf(source.id).take(MAX_PING_SERVERS)
                engine.startPingBatch(source.id, guids)
            }
            showBanner("Testing latency on real servers")
        }
    }

    fun pingManualServers() {
        val guids = _uiState.value.manualServers.map { it.guid }
        if (guids.isEmpty()) {
            showBanner("No manual configuration to test", isError = true)
            return
        }
        _uiState.value = _uiState.value.copy(pingRunning = true)
        engine.startPingBatch(HardVpnRepository.MANUAL_BUCKET, guids)
    }

    fun pingSingle(domain: ConfigDomain, guid: String) {
        _uiState.value = _uiState.value.copy(pingRunning = true)
        engine.startPingBatch("", listOf(guid))
    }

    // ------------------------------------------------------------------
    // Manual import
    // ------------------------------------------------------------------

    fun importText(text: String?) {
        if (_uiState.value.importing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importing = true)
            val result = repository.importManualText(text)
            _uiState.value = _uiState.value.copy(importing = false)
            loadManual()
            showBanner(result.message, isError = !result.success)
        }
    }

    fun deleteManual(guid: String) {
        val removed = repository.removeManualProfile(guid)
        loadManual()
        showBanner(
            if (removed) "Configuration deleted" else "This configuration cannot be deleted here",
            isError = !removed,
        )
    }

    fun renameManual(guid: String, newName: String) {
        val renamed = repository.renameManualProfile(guid, newName)
        loadManual()
        showBanner(
            if (renamed) "Configuration renamed" else "Name cannot be empty",
            isError = !renamed,
        )
    }

    // ------------------------------------------------------------------
    // Boost Security
    // ------------------------------------------------------------------

    fun setBoostSecurity(enabled: Boolean) {
        val written = repository.setLocalDnsEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            boostSecurityEnabled = if (written) repository.isLocalDnsEnabled() else !enabled
        )
        showBanner(
            when {
                !written -> "This device could not store the security setting"
                _uiState.value.isConnected ->
                    "Saved. It takes effect on the next connection."

                enabled -> "Local DNS protection enabled for the next connection"
                else -> "Local DNS protection disabled for the next connection"
            },
            isError = !written,
        )
    }

    // ------------------------------------------------------------------
    // DNS Gaming
    // ------------------------------------------------------------------

    fun refreshDns() {
        _uiState.value = _uiState.value.copy(
            dns = DnsUiState(
                profiles = repository.dnsProfiles(),
                activeProfileId = repository.activeDnsProfileId(),
                remoteDns = repository.currentRemoteDns(),
                vpnDns = repository.currentVpnDns(),
            )
        )
    }

    fun saveDnsProfile(profile: DnsProfile) {
        val saved = repository.saveDnsProfile(profile)
        refreshDns()
        showBanner(
            if (saved) {
                "DNS profile saved"
            } else {
                "Enter a valid IPv4 or IPv6 resolver address"
            },
            isError = !saved,
        )
    }

    fun deleteDnsProfile(profileId: String) {
        repository.removeDnsProfile(profileId)
        refreshDns()
        showBanner("DNS profile deleted")
    }

    fun applyDnsPreset(name: String, primary: String, secondary: String) {
        applyDnsValue(profileId = null, name = name, primary = primary, secondary = secondary)
    }

    fun applyDnsProfile(profile: DnsProfile) {
        applyDnsValue(profile.id, profile.name, profile.primary, profile.secondary)
    }

    private fun applyDnsValue(
        profileId: String?,
        name: String,
        primary: String,
        secondary: String,
    ) {
        val applied = repository.applyDns(profileId, primary, secondary)
        if (!applied) {
            showBanner("Enter a valid IPv4 or IPv6 resolver address", isError = true)
            return
        }
        val running = _uiState.value.phase == ConnectionPhase.Connected
        if (running) {
            // DNS is read when the core starts, so the tunnel must be restarted to apply it.
            engine.restartIfRunning()
        }
        refreshDns()
        _uiState.value = _uiState.value.copy(
            dns = _uiState.value.dns.copy(runningTunnelNeedsReconnect = running)
        )
        showBanner(
            when {
                running -> "$name applied. Reconnecting to activate it."
                else -> "$name applied. It will be used on the next connection."
            }
        )
    }

    fun requestEngineRestart() {
        engine.restartIfRunning()
    }

    fun dismissBanner(id: String) {
        if (_uiState.value.banner?.id == id) {
            _uiState.value = _uiState.value.copy(banner = null)
        }
    }

    fun showBanner(message: String, isError: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            banner = UiBanner(message = message, isError = isError)
        )
    }

    fun onDestinationSelected(destination: HardVpnDestination) {
        _uiState.value = _uiState.value.copy(destination = destination)
        if (destination == HardVpnDestination.ImportConfig) loadManual()
        if (destination == HardVpnDestination.DnsGaming) refreshDns()
    }

    override fun onCleared() {
        super.onCleared()
        connectTimeoutJob?.cancel()
        engine.close()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val AUTO_PING_TIMEOUT_MS = 20_000L
        private const val AUTO_PING_POLL_MS = 1_500L
        private const val MAX_AUTO_PING_SOURCES = 3
        private const val MAX_AUTO_PING_SERVERS = 25
        private const val MAX_PING_SOURCES = 20
        private const val MAX_PING_SERVERS = 250
    }
}
