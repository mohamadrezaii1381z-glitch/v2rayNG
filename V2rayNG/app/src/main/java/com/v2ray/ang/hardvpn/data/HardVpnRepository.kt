package com.v2ray.ang.hardvpn.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.TrafficSample
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.hardvpn.domain.AddSourceResult
import com.v2ray.ang.hardvpn.domain.AutoSelectPolicy
import com.v2ray.ang.hardvpn.domain.ConnectionPhase
import com.v2ray.ang.hardvpn.domain.CountryHints
import com.v2ray.ang.hardvpn.domain.DnsProfile
import com.v2ray.ang.hardvpn.domain.DomainSnapshot
import com.v2ray.ang.hardvpn.domain.HardVpnDomain
import com.v2ray.ang.hardvpn.domain.HardVpnFormat
import com.v2ray.ang.hardvpn.domain.ManualImportResult
import com.v2ray.ang.hardvpn.domain.ManualServerUiModel
import com.v2ray.ang.hardvpn.domain.RefreshOutcome
import com.v2ray.ang.hardvpn.domain.SafeDisplayName
import com.v2ray.ang.hardvpn.domain.ServerUiModel
import com.v2ray.ang.hardvpn.domain.SourceUiModel
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Connection truth as reported by the daemon broadcasts, not by UI intent. */
data class ConnectionSnapshot(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val activeGuid: String? = null,
    val activeServerName: String? = null,
    val connectedSinceMillis: Long? = null,
)

/** A latency run owned by one request id, so late replies cannot complete a newer run. */
data class PingBatch(
    val requestId: String? = null,
    val running: Boolean = false,
    val results: Map<String, Long> = emptyMap(),
)

sealed interface HardVpnEvent {
    data object StartFailure : HardVpnEvent
    data object StartSuccess : HardVpnEvent
    data object Stopped : HardVpnEvent
    data class PingFinished(val requestId: String) : HardVpnEvent
}

/**
 * The single UI-to-engine boundary for HardVPN.
 *
 * It reuses the existing v2rayNG core, services, broadcast contract, subscription updater, test
 * service, profile storage, and settings. No second engine, service, or database is introduced.
 * The only engine addition is the daemon-side traffic sample reply the UI cannot obtain otherwise.
 */
class HardVpnRepository(private val app: AngApplication) : Closeable {

    companion object {
        /** Group that owns configurations imported by the user in the Manual Import destination. */
        const val MANUAL_GROUP_ID = "HARDVPN_MANUAL"

        private const val TRAFFIC_REQUEST_ID = "hardvpn-traffic"
        private const val MIN_TRAFFIC_INTERVAL_MS = 750L

        /** Cloudflare's resolvers, used by both the DNS preset and Boost Security. */
        private const val BOOST_PRIMARY_DNS = "1.1.1.1"
        private const val BOOST_SECONDARY_DNS = "1.0.0.1"
        private const val BOOST_REMOTE_DNS = "https://cloudflare-dns.com/dns-query"
    }

    private val localizedContext: Context
        get() = AppLocaleManager.localizedContext(app)

    private val closed = AtomicBoolean(false)

    private val _connection = MutableStateFlow(ConnectionSnapshot())
    val connection: StateFlow<ConnectionSnapshot> = _connection.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSample())
    val traffic: StateFlow<TrafficSample> = _traffic.asStateFlow()

    private val _ping = MutableStateFlow(PingBatch())
    val ping: StateFlow<PingBatch> = _ping.asStateFlow()

    private val _events = MutableSharedFlow<HardVpnEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<HardVpnEvent> = _events.asSharedFlow()

    private var lastTrafficRequestAt = 0L

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val requestId = safeIntent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID).orEmpty()
            when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> markRunning()
                AppConfig.MSG_STATE_NOT_RUNNING -> markStopped()
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    markRunning()
                    _events.tryEmit(HardVpnEvent.StartSuccess)
                    requestTrafficSample(force = true)
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    LogUtil.e(AppConfig.TAG, "HardVPN: daemon reported a start failure")
                    _connection.update {
                        it.copy(phase = ConnectionPhase.ERROR, connectedSinceMillis = null)
                    }
                    _traffic.value = TrafficSample()
                    _events.tryEmit(HardVpnEvent.StartFailure)
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    markStopped()
                    _events.tryEmit(HardVpnEvent.Stopped)
                }

                AppConfig.MSG_QUERY_TRAFFIC_RESULT -> {
                    safeIntent.serializable<TrafficSample>("content")?.let(::applyTrafficSample)
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val result = safeIntent.serializable<RealPingResult>("content") ?: return
                    _ping.update { batch ->
                        if (batch.requestId != requestId) batch
                        else batch.copy(results = batch.results + (result.guid to result.delayMillis))
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_FINISH,
                AppConfig.MSG_MEASURE_CONFIG_CANCEL -> {
                    _ping.update { batch ->
                        if (batch.requestId != requestId) batch else batch.copy(running = false)
                    }
                    if (requestId.isNotEmpty()) {
                        _events.tryEmit(HardVpnEvent.PingFinished(requestId))
                    }
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            serviceReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags(),
        )
        // The daemon answers with its real state, or stays silent when it is not running.
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { MessageHelper.sendMsg2Service(app, AppConfig.MSG_UNREGISTER_CLIENT, "") }
            .onFailure { LogUtil.e(AppConfig.TAG, "HardVPN: failed to unregister daemon client", it) }
        runCatching { app.unregisterReceiver(serviceReceiver) }
            .onFailure { LogUtil.e(AppConfig.TAG, "HardVPN: failed to unregister receiver", it) }
    }

    // ---------- Connection lifecycle ----------

    private fun markRunning() {
        _connection.update { previous ->
            val guid = MmkvManager.getSelectServer() ?: previous.activeGuid
            previous.copy(
                phase = ConnectionPhase.CONNECTED,
                activeGuid = guid,
                activeServerName = safeNameFor(guid),
                // The daemon's authoritative start time arrives with the next traffic sample.
                connectedSinceMillis = previous.connectedSinceMillis
                    ?: System.currentTimeMillis(),
            )
        }
    }

    private fun markStopped() {
        _connection.update {
            it.copy(
                phase = ConnectionPhase.DISCONNECTED,
                connectedSinceMillis = null,
            )
        }
        _traffic.value = TrafficSample()
    }

    /** Validates the selection, marks the attempt, then asks the existing launcher to start. */
    fun start(guid: String?) {
        val target = guid ?: MmkvManager.getSelectServer()
        if (target.isNullOrBlank() || MmkvManager.decodeServerConfig(target) == null) {
            _connection.update { it.copy(phase = ConnectionPhase.ERROR) }
            _events.tryEmit(HardVpnEvent.StartFailure)
            return
        }
        MmkvManager.setSelectServer(target)
        _connection.value = ConnectionSnapshot(
            phase = ConnectionPhase.CONNECTING,
            activeGuid = target,
            activeServerName = safeNameFor(target),
            connectedSinceMillis = null,
        )
        LauncherManager.startService(app)
    }

    fun stop() {
        _connection.update { it.copy(phase = ConnectionPhase.DISCONNECTING) }
        LauncherManager.stopService(app)
    }

    /** Applies a new selection to the running daemon through the existing restart path. */
    fun restart(guid: String) {
        MmkvManager.setSelectServer(guid)
        _ping.update { it.copy(running = false) }
        _connection.value = ConnectionSnapshot(
            phase = ConnectionPhase.CONNECTING,
            activeGuid = guid,
            activeServerName = safeNameFor(guid),
            connectedSinceMillis = null,
        )
        LauncherManager.restartService(app)
    }

    fun selectServer(guid: String) {
        MmkvManager.setSelectServer(guid)
        _connection.update { it.copy(activeGuid = guid) }
    }

    /** Auto Select records the winner as the domain selection while keeping automatic mode on. */
    fun applyAutoSelection(domain: HardVpnDomain, guid: String) {
        HardVpnStorage.setAutoSelect(domain, true)
        HardVpnStorage.setSelectedGuid(domain, guid)
        selectServer(guid)
    }

    /** Aborts a start attempt that never produced a daemon confirmation. */
    fun abortAttempt() {
        if (!closed.get()) {
            LauncherManager.stopService(app)
        }
        _connection.update {
            it.copy(phase = ConnectionPhase.ERROR, connectedSinceMillis = null)
        }
        _traffic.value = TrafficSample()
    }

    /**
     * Clears an unconfirmed stop and re-asks the daemon for its state, so a still-running core
     * immediately reports RUNNING again instead of leaving a false disconnected state.
     */
    fun reconcileAfterUnconfirmedStop() {
        _connection.update {
            if (it.phase == ConnectionPhase.DISCONNECTING) {
                it.copy(phase = ConnectionPhase.DISCONNECTED, connectedSinceMillis = null)
            } else {
                it
            }
        }
        _traffic.value = TrafficSample()
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    fun currentEngineSelection(): String? = MmkvManager.getSelectServer()

    fun safeNameFor(guid: String?): String? {
        if (guid.isNullOrBlank()) return null
        val profile = MmkvManager.decodeServerConfig(guid)
        SafeDisplayName.sanitize(profile?.remarks)?.let { return it }
        return localizedContext.getString(
            R.string.hardvpn_server_fallback_name,
            HardVpnFormat.fallbackSuffix(guid),
        )
    }

    // ---------- Traffic ----------

    /**
     * Requests one daemon-measured sample. Called only while the Home screen is visible and the
     * tunnel is connected, throttled so the UI cannot busy-poll the daemon.
     */
    fun requestTrafficSample(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastTrafficRequestAt < MIN_TRAFFIC_INTERVAL_MS) return
        lastTrafficRequestAt = now
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_QUERY_TRAFFIC, TRAFFIC_REQUEST_ID)
    }

    private fun applyTrafficSample(sample: TrafficSample) {
        _traffic.value = sample
        if (sample.connectedAtMillis > 0L) {
            _connection.update { it.copy(connectedSinceMillis = sample.connectedAtMillis) }
        }
    }

    // ---------- Latency ----------

    fun startPing(guids: List<String>, onlyTcp: Boolean, requestId: String): Boolean {
        if (guids.isEmpty()) return false
        _ping.value = PingBatch(requestId = requestId, running = true, results = emptyMap())
        MessageHelper.sendMsg2TestService(
            app,
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_START,
                subscriptionId = "",
                serverGuids = guids,
                onlyTcp = onlyTcp,
            ),
            requestId,
        )
        return true
    }

    fun cancelPing() {
        _ping.update { it.copy(running = false) }
        MessageHelper.sendMsg2TestService(
            app,
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL),
        )
    }

    /** Candidates for Auto Select, capped so a large list never starts an unbounded probe run. */
    fun autoSelectCandidates(domain: HardVpnDomain): List<String> =
        AutoSelectPolicy.candidateGuids(loadDomain(domain).servers)

    // ---------- Domains ----------

    fun loadDomain(domain: HardVpnDomain): DomainSnapshot {
        val running = _connection.value.phase == ConnectionPhase.CONNECTED
        val activeGuid = MmkvManager.getSelectServer()
        val selectedGuid = HardVpnStorage.selectedGuid(domain)
        val sourceIds = HardVpnStorage.sourceIds(domain)

        val servers = ArrayList<ServerUiModel>()
        sourceIds.forEach { sourceId ->
            MmkvManager.decodeServerList(sourceId).forEach { guid ->
                val profile = MmkvManager.decodeServerConfig(guid) ?: return@forEach
                val name = SafeDisplayName.sanitize(profile.remarks)
                    ?: localizedContext.getString(
                        R.string.hardvpn_server_fallback_name,
                        HardVpnFormat.fallbackSuffix(guid),
                    )
                val hint = CountryHints.resolve(name)
                val delay = MmkvManager.decodeServerAffiliationInfo(guid)
                    ?.testDelayMillis
                    ?.takeIf { it >= 0L }
                servers += ServerUiModel(
                    guid = guid,
                    name = name,
                    flag = hint?.flag,
                    location = hint?.name,
                    pingMillis = delay,
                    isSelected = guid == selectedGuid,
                    isActive = running && guid == activeGuid,
                )
            }
        }

        val sources = sourceIds.mapNotNull { id ->
            val item = MmkvManager.decodeSubscription(id) ?: return@mapNotNull null
            SourceUiModel(
                id = id,
                name = item.remarks,
                serverCount = MmkvManager.decodeServerList(id).size,
                lastUpdatedMillis = item.lastUpdated,
            )
        }

        return DomainSnapshot(
            servers = servers,
            sources = sources,
            selectedGuid = selectedGuid,
            autoSelect = HardVpnStorage.autoSelect(domain),
        )
    }

    /**
     * Refreshes every source of one domain. The existing updater only replaces a group's list after
     * a successful parse, so a failed source keeps its last-known-good configurations.
     */
    fun refreshDomain(domain: HardVpnDomain): RefreshOutcome {
        var success = 0
        var failure = 0
        var configs = 0
        HardVpnStorage.sourceIds(domain).forEach { id ->
            val item = MmkvManager.decodeSubscription(id)
            if (item == null) {
                failure++
                return@forEach
            }
            val result = AngConfigManager.updateConfigViaSub(SubscriptionCache(id, item))
            success += result.successCount
            failure += result.failureCount
            configs += result.configCount
        }
        LogUtil.i(
            AppConfig.TAG,
            "HardVPN: refreshed ${domain.key} domain, success=$success failure=$failure",
        )
        return RefreshOutcome(successCount = success, failureCount = failure, configCount = configs)
    }

    fun refreshSource(sourceId: String): RefreshOutcome {
        val item = MmkvManager.decodeSubscription(sourceId)
            ?: return RefreshOutcome(successCount = 0, failureCount = 1, configCount = 0)
        val result = AngConfigManager.updateConfigViaSub(SubscriptionCache(sourceId, item))
        return RefreshOutcome(
            successCount = result.successCount,
            failureCount = result.failureCount,
            configCount = result.configCount,
        )
    }

    /**
     * Registers a subscription as a source of exactly one domain. The URL is validated and stored
     * only in v2rayNG's subscription bucket; it is never logged or shown outside the source editor.
     */
    fun addSource(domain: HardVpnDomain, displayName: String, url: String): AddSourceResult {
        val trimmedUrl = url.trim()
        if (!Utils.isValidSubUrl(trimmedUrl)) {
            return AddSourceResult.InvalidUrl
        }
        val guid = "hardvpn-${domain.key}-${Utils.getUuid()}"
        val item = SubscriptionItem(
            remarks = displayName.trim().ifEmpty {
                localizedContext.getString(R.string.hardvpn_source_default_name)
            },
            url = trimmedUrl,
            enabled = true,
            autoUpdate = false,
        )
        MmkvManager.encodeSubscription(guid, item)
        HardVpnStorage.addSourceId(domain, guid)

        val result = AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, item))
        LogUtil.i(
            AppConfig.TAG,
            "HardVPN: added ${domain.key} source, success=${result.successCount} failure=${result.failureCount}",
        )
        return when {
            result.successCount > 0 && result.configCount > 0 ->
                AddSourceResult.Success(result.configCount)

            result.failureCount > 0 -> AddSourceResult.Failed
            else -> AddSourceResult.Empty
        }
    }

    fun updateSourceName(sourceId: String, displayName: String) {
        val item = MmkvManager.decodeSubscription(sourceId) ?: return
        val name = displayName.trim()
        if (name.isEmpty()) return
        item.remarks = name
        MmkvManager.encodeSubscription(sourceId, item)
    }

    fun deleteSource(domain: HardVpnDomain, sourceId: String) {
        val ownedGuids = MmkvManager.decodeServerList(sourceId).toSet()
        HardVpnStorage.removeSourceId(domain, sourceId)
        MmkvManager.removeServerViaSubid(sourceId)
        MmkvManager.removeSubscription(sourceId)
        // A selection that lived in the deleted source must not survive as a dangling GUID.
        if ((HardVpnStorage.selectedGuid(domain) ?: "") in ownedGuids) {
            HardVpnStorage.setSelectedGuid(domain, null)
        }
        if ((HardVpnStorage.selectedManualGuid() ?: "") in ownedGuids) {
            HardVpnStorage.setSelectedManualGuid(null)
        }
    }

    fun selectDomainServer(domain: HardVpnDomain, guid: String, autoSelect: Boolean) {
        HardVpnStorage.setSelectedGuid(domain, guid)
        HardVpnStorage.setAutoSelect(domain, autoSelect)
        selectServer(guid)
    }

    fun useAutoSelect(domain: HardVpnDomain) {
        HardVpnStorage.setAutoSelect(domain, true)
        HardVpnStorage.setSelectedGuid(domain, null)
    }

    fun setAutoSelect(domain: HardVpnDomain, enabled: Boolean) {
        HardVpnStorage.setAutoSelect(domain, enabled)
    }

    // ---------- Manual import ----------

    fun loadManualServers(): List<ManualServerUiModel> {
        val running = _connection.value.phase == ConnectionPhase.CONNECTED
        val activeGuid = MmkvManager.getSelectServer()
        return MmkvManager.decodeServerList(MANUAL_GROUP_ID).mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val delay = MmkvManager.decodeServerAffiliationInfo(guid)
                ?.testDelayMillis
                ?.takeIf { it >= 0L }
            ManualServerUiModel(
                guid = guid,
                name = profile.remarks.trim().ifEmpty {
                    localizedContext.getString(
                        R.string.hardvpn_server_fallback_name,
                        HardVpnFormat.fallbackSuffix(guid),
                    )
                },
                summary = profile.description,
                pingMillis = delay,
                isSelected = guid == HardVpnStorage.selectedManualGuid(),
                isActive = running && guid == activeGuid,
                configTypeValue = profile.configType.value,
            )
        }
    }

    fun selectManualServer(guid: String) {
        HardVpnStorage.setSelectedManualGuid(guid)
        selectServer(guid)
    }

    /**
     * Imports pasted, scanned, or file-loaded configurations into the manual bucket only.
     *
     * A lone subscription URL is rejected here so manual import can never create a hidden
     * subscription source; the user adds sources in Home VPN or ExploreVPN instead.
     */
    fun importManual(text: String): ManualImportResult {
        val content = text.trim()
        if (content.isEmpty()) return ManualImportResult(count = 0, sourceUrlRejected = false)
        if (Utils.isValidSubUrl(content)) {
            return ManualImportResult(count = 0, sourceUrlRejected = true)
        }
        val (count, subscriptionCount) =
            AngConfigManager.importBatchConfig(content, MANUAL_GROUP_ID, true)
        return ManualImportResult(
            count = count,
            sourceUrlRejected = false,
            subscriptionSourcesIgnored = subscriptionCount,
        )
    }

    fun renameManualServer(guid: String, displayName: String): Boolean {
        val name = displayName.trim()
        if (name.isEmpty()) return false
        val profile = MmkvManager.decodeServerConfig(guid) ?: return false
        val updated = profile.copy(remarks = name)
        MmkvManager.encodeServerConfig(guid, updated)
        return true
    }

    fun deleteManualServer(guid: String) {
        MmkvManager.removeServer(guid)
        if (HardVpnStorage.selectedManualGuid() == guid) {
            HardVpnStorage.setSelectedManualGuid(null)
        }
    }

    fun pingAllManual(): List<String> = MmkvManager.decodeServerList(MANUAL_GROUP_ID)

    fun domainGuids(domain: HardVpnDomain): List<String> =
        HardVpnStorage.sourceIds(domain).flatMap { MmkvManager.decodeServerList(it) }

    // ---------- DNS ----------

    fun appliedDns(): Pair<String?, String?> {
        val servers = SettingsManager.getVpnDnsServers()
        return servers.firstOrNull() to servers.getOrNull(1)
    }

    fun applyVpnDns(primary: String, secondary: String?): Boolean {
        val value = listOfNotNull(
            primary.trim().takeIf { it.isNotEmpty() },
            secondary?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(",")
        if (value.isEmpty()) return false
        return MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, value)
    }

    fun dnsProfiles(): List<DnsProfile> = HardVpnStorage.dnsProfiles()

    fun saveDnsProfile(profile: DnsProfile) {
        val profiles = HardVpnStorage.dnsProfiles().filterNot { it.id == profile.id }
        HardVpnStorage.saveDnsProfiles(profiles + profile)
    }

    fun deleteDnsProfile(id: String) {
        HardVpnStorage.saveDnsProfiles(HardVpnStorage.dnsProfiles().filterNot { it.id == id })
    }

    // ---------- Boost Security ----------

    /**
     * Boost Security is real: it writes the encrypted-DNS preset the core reads for the tunnel and
     * for remote resolution. Its state is always derived from those settings, never stored as an
     * independent flag, so the card cannot claim protection the engine does not have.
     */
    fun isBoostSecurityActive(): Boolean {
        val (primary, secondary) = appliedDns()
        val remoteDns = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS)
        return primary == BOOST_PRIMARY_DNS &&
            secondary == BOOST_SECONDARY_DNS &&
            remoteDns == BOOST_REMOTE_DNS
    }

    fun setBoostSecurityEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            HardVpnStorage.saveBoostBackup(
                vpnDns = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS),
                remoteDns = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS),
            )
            val vpnOk = MmkvManager.encodeSettings(
                AppConfig.PREF_VPN_DNS,
                "$BOOST_PRIMARY_DNS,$BOOST_SECONDARY_DNS",
            )
            val remoteOk = MmkvManager.encodeSettings(
                AppConfig.PREF_REMOTE_DNS,
                BOOST_REMOTE_DNS,
            )
            vpnOk && remoteOk
        } else {
            val vpnDns = HardVpnStorage.boostBackupVpnDns() ?: AppConfig.DNS_VPN
            val remoteDns = HardVpnStorage.boostBackupRemoteDns() ?: AppConfig.DNS_PROXY
            val vpnOk = MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, vpnDns)
            val remoteOk = MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, remoteDns)
            if (vpnOk && remoteOk) HardVpnStorage.clearBoostBackup()
            vpnOk && remoteOk
        }
    }

    // ---------- Misc ----------

    fun isVpnMode(): Boolean = SettingsManager.isVpnMode()

    fun manualConfigType(guid: String): Int? =
        MmkvManager.decodeServerConfig(guid)?.configType?.value

    fun configTypeOf(guid: String): EConfigType? =
        MmkvManager.decodeServerConfig(guid)?.configType

    fun hasAnyDomainSources(domain: HardVpnDomain): Boolean =
        HardVpnStorage.sourceIds(domain).isNotEmpty()

    fun profileFor(guid: String): ProfileItem? = MmkvManager.decodeServerConfig(guid)
}
