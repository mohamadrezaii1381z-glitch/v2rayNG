package com.v2ray.ang.ui.hardvpn.data

import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.hardvpn.ConfigDomain
import com.v2ray.ang.ui.hardvpn.DnsProfile
import com.v2ray.ang.ui.hardvpn.DomainSnapshot
import com.v2ray.ang.ui.hardvpn.ManualImportResult
import com.v2ray.ang.ui.hardvpn.RefreshReport
import com.v2ray.ang.ui.hardvpn.SafeServer
import com.v2ray.ang.ui.hardvpn.SourceUi
import com.v2ray.ang.ui.hardvpn.countryDisplayName
import com.v2ray.ang.ui.hardvpn.detectCountryCode
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Storage + lifecycle layer for the three independent HardVPN configuration domains.
 *
 * Ownership rules enforced here:
 *  - A subscription (source) belongs to exactly one domain; the domain index is a JSON list
 *    of source ids kept in MMKV settings under [sourcesKey].
 *  - Home and ExploreVPN servers are only ever read through their own source ids, so the two
 *    lists can never merge, even though both live in v2rayNG's existing profile storage.
 *  - Manual imports use a dedicated bucket that is never referenced by a domain index.
 *  - Subscription URLs are never returned to the UI. Only names and counts are exposed.
 *
 * Nothing in this class writes to the existing v2rayNG profiles of a user who never used
 * HardVPN: sources are created lazily and migration is additive.
 */
class HardVpnRepository(private val app: AngApplication) {

    companion object {
        /** Bucket id for configurations the user imported by hand. */
        const val MANUAL_BUCKET = "hardvpn_manual"

        private const val PREFIX = "hardvpn_"
        private const val SELECTED_HOME = "hardvpn_selected_home"
        private const val SELECTED_EXPLORE = "hardvpn_selected_explore"
        private const val SELECTED_MANUAL = "hardvpn_selected_manual"
        private const val AUTO_SELECT_HOME = "hardvpn_auto_select_home"
        private const val ACTIVE_DOMAIN = "hardvpn_active_domain"
        private const val DNS_PROFILES = "hardvpn_dns_profiles"
        private const val DNS_ACTIVE = "hardvpn_dns_active_profile"

        /** Verified preset: Cloudflare primary/secondary resolvers used by DNS Gaming. */
        const val CLOUDFLARE_PRIMARY = "1.1.1.1"
        const val CLOUDFLARE_SECONDARY = "1.0.0.1"

        /**
         * Electro DNS: no verified resolver address exists in this repository and none is
         * invented here. The preset stays unavailable until a real address is supplied.
         */
        const val ELECTRO_PRIMARY: String? = null
    }

    private fun sourcesKey(domain: ConfigDomain) = "${PREFIX}sources_${domain.storageTag}"

    private fun selectedKey(domain: ConfigDomain) = when (domain) {
        ConfigDomain.Home -> SELECTED_HOME
        ConfigDomain.Explore -> SELECTED_EXPLORE
        ConfigDomain.Manual -> SELECTED_MANUAL
    }

    private fun sourceFailureKey(sourceId: String) = "${PREFIX}source_failed_$sourceId"

    // ------------------------------------------------------------------
    // Domain index
    // ------------------------------------------------------------------

    /** Source ids owned by [domain]. Never includes ids of another domain. */
    fun sourceIds(domain: ConfigDomain): List<String> {
        val json = MmkvManager.decodeSettingsString(sourcesKey(domain)) ?: return emptyList()
        return JsonUtil.fromJsonSafe(json, Array<String>::class.java)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    private fun storeSourceIds(domain: ConfigDomain, ids: List<String>) {
        MmkvManager.encodeSettings(
            sourcesKey(domain),
            JsonUtil.toJson(ids.distinct().toMutableList())
        )
    }

    /** Owning domain of a source id, derived from the indexes only. */
    fun domainOfSource(sourceId: String): ConfigDomain? =
        ConfigDomain.entries.firstOrNull { sourceIds(it).contains(sourceId) }

    /** Owning domain of a stored profile, so the active configuration always has an origin. */
    fun domainOfProfile(guid: String?): ConfigDomain? {
        val safeGuid = guid?.takeIf { it.isNotBlank() } ?: return null
        val profile = MmkvManager.decodeServerConfig(safeGuid) ?: return null
        return domainOfSource(profile.subscriptionId)
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    suspend fun load(domain: ConfigDomain): DomainSnapshot = withContext(Dispatchers.IO) {
        val sources = loadSources(domain)
        val servers = ArrayList<SafeServer>(sources.sumOf { it.serverCount }.coerceAtLeast(0))
        sources.forEach { source ->
            MmkvManager.decodeServerList(source.id).forEach { guid ->
                toSafeServer(guid, domain, source.id, source.name)?.let { servers.add(it) }
            }
        }
        DomainSnapshot(sources = sources, servers = servers)
    }

    private fun loadSources(domain: ConfigDomain): List<SourceUi> =
        sourceIds(domain).mapNotNull { id ->
            val item = MmkvManager.decodeSubscription(id) ?: return@mapNotNull null
            SourceUi(
                id = id,
                name = sanitizeName(item.remarks).ifBlank { "Source" },
                serverCount = MmkvManager.decodeServerList(id).size,
                lastUpdatedAt = item.lastUpdated,
                failedLastRefresh = MmkvManager.decodeSettingsBool(sourceFailureKey(id), false),
            )
        }

    /** Manual bucket contents, sorted with the newest import first. */
    suspend fun loadManual(): List<SafeServer> = withContext(Dispatchers.IO) {
        MmkvManager.decodeServerList(MANUAL_BUCKET).mapNotNull { guid ->
            toSafeServer(guid, ConfigDomain.Manual, MANUAL_BUCKET, "Manual import")
        }
    }

    /**
     * Projects one stored profile into the safe display model.
     *
     * Only the friendly remark, a country hint derived from that remark and the persisted
     * ping are copied. Addresses, ports, ids, keys, transport and TLS values are dropped here
     * so no downstream screen can render them.
     */
    private fun toSafeServer(
        guid: String,
        domain: ConfigDomain,
        sourceId: String,
        sourceName: String,
    ): SafeServer? {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        val remark = sanitizeName(profile.remarks)
        val countryCode = detectCountryCode(remark)
        val location = countryDisplayName(countryCode).orEmpty()
        val ping = MmkvManager.decodeServerAffiliationInfo(guid)
            ?.testDelayMillis
            ?.takeIf { it > 0L }
        return SafeServer(
            guid = guid,
            displayName = remark.ifBlank { "Unnamed server" },
            location = location,
            countryCode = countryCode,
            pingMs = ping,
            domain = domain,
            sourceId = sourceId,
            sourceName = sourceName,
        )
    }

    /**
     * Removes technical tokens from a user-facing remark. A remark comes from a remote panel,
     * so it is treated as untrusted text: URLs, host:port pairs, IP addresses and UUIDs are
     * stripped even though the remark is usually a friendly name.
     */
    private fun sanitizeName(raw: String?): String {
        var text = raw.orEmpty().trim()
        if (text.isEmpty()) return ""
        text = text.replace(Regex("(?i)[a-z][a-z0-9+.-]*://\\S+"), " ")
        text = text.replace(
            Regex("(?i)\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}\\b(?::\\d{1,5})?"),
            " "
        )
        text = text.replace(
            Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b(?::\\d{1,5})?"),
            " "
        )
        text = text.replace(
            Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"),
            " "
        )
        text = text.replace(Regex("\\s+"), " ").trim().trim('-', '_', '|', ':')
        return text.take(48)
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    fun selectedGuid(domain: ConfigDomain): String? =
        MmkvManager.decodeSettingsString(selectedKey(domain))?.takeIf { it.isNotBlank() }

    /** Selects a profile for [domain] and mirrors it into the engine's selected server. */
    fun selectServer(guid: String, domain: ConfigDomain) {
        MmkvManager.encodeSettings(selectedKey(domain), guid)
        MmkvManager.encodeSettings(ACTIVE_DOMAIN, domain.name)
        MmkvManager.setSelectServer(guid)
    }

    /** Domain that currently owns the engine configuration, if it is still known. */
    fun activeDomain(): ConfigDomain {
        val stored = MmkvManager.decodeSettingsString(ACTIVE_DOMAIN)
        domainOfProfile(MmkvManager.getSelectServer())?.let { return it }
        return ConfigDomain.entries.firstOrNull { it.name == stored } ?: ConfigDomain.Home
    }

    fun isAutoSelect(domain: ConfigDomain): Boolean =
        domain == ConfigDomain.Home &&
                MmkvManager.decodeSettingsBool(AUTO_SELECT_HOME, false)

    fun setAutoSelect(enabled: Boolean) {
        MmkvManager.encodeSettings(AUTO_SELECT_HOME, enabled)
    }

    /** Servers of [domain] that can be used for a real connection attempt. */
    suspend fun eligibleServers(domain: ConfigDomain): List<SafeServer> =
        load(domain).servers

    // ------------------------------------------------------------------
    // Sources (subscriptions)
    // ------------------------------------------------------------------

    sealed interface AddSourceResult {
        data class Added(val sourceId: String) : AddSourceResult
        data class Rejected(val reason: String) : AddSourceResult
    }

    /**
     * Adds a source to [domain] and imports its first batch.
     *
     * The URL is validated with v2rayNG's own rules, stored through the existing subscription
     * storage and never echoed back to the caller or logged.
     */
    suspend fun addSource(
        domain: ConfigDomain,
        name: String,
        url: String,
    ): AddSourceResult = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val displayName = sanitizeName(name).ifBlank { "Source" }

        if (trimmedUrl.isEmpty()) return@withContext AddSourceResult.Rejected("Subscription URL is required")
        if (!Utils.isValidSubUrl(trimmedUrl)) {
            return@withContext AddSourceResult.Rejected("This is not a supported subscription URL")
        }
        val duplicate = ConfigDomain.entries
            .filter { it != ConfigDomain.Manual }
            .flatMap { sourceIds(it) }
            .any { MmkvManager.decodeSubscription(it)?.url == trimmedUrl }
        if (duplicate) {
            return@withContext AddSourceResult.Rejected("This source already exists")
        }

        val sourceId = UUID.randomUUID().toString()
        val item = SubscriptionItem(
            remarks = displayName,
            url = trimmedUrl,
            enabled = true,
            autoUpdate = domain == ConfigDomain.Home,
            updateInterval = 1440L,
        )
        runCatching { MmkvManager.encodeSubscription(sourceId, item) }
            .onFailure { return@withContext AddSourceResult.Rejected("Could not save the source") }

        storeSourceIds(domain, sourceIds(domain) + sourceId)

        val result = refreshSource(sourceId)
        if (result.failedSources > 0 && result.updatedSources == 0) {
            // Keep the source so the user can retry; report the failure honestly.
            return@withContext AddSourceResult.Added(sourceId)
        }
        if (domain == ConfigDomain.Home) {
            runCatching { SubscriptionUpdater.syncOne(app, sourceId) }
        }
        AddSourceResult.Added(sourceId)
    }

    fun renameSource(domain: ConfigDomain, sourceId: String, newName: String) {
        if (!sourceIds(domain).contains(sourceId)) return
        val item = MmkvManager.decodeSubscription(sourceId) ?: return
        item.remarks = sanitizeName(newName).ifBlank { item.remarks }
        MmkvManager.encodeSubscription(sourceId, item)
    }

    /** Removes a source and only that source's profiles. */
    fun removeSource(domain: ConfigDomain, sourceId: String) {
        if (!sourceIds(domain).contains(sourceId)) return
        runCatching { SubscriptionUpdater.cancelOne(app, sourceId) }
        MmkvManager.removeSubscription(sourceId)
        MmkvManager.encodeSettings(sourceFailureKey(sourceId), false)
        storeSourceIds(domain, sourceIds(domain) - sourceId)
        if (selectedGuid(domain)?.let { MmkvManager.decodeServerConfig(it) == null } == true) {
            MmkvManager.encodeSettings(selectedKey(domain), "")
        }
    }

    /**
     * Refreshes every source of [domain], or a single one when [onlySourceId] is set.
     *
     * Failure isolation: each source is updated through v2rayNG's own subscription updater,
     * which only replaces stored profiles after a successful, parseable download. A failed or
     * empty response therefore leaves the previous configurations untouched.
     */
    suspend fun refresh(
        domain: ConfigDomain,
        onlySourceId: String? = null,
    ): RefreshReport = withContext(Dispatchers.IO) {
        val ids = sourceIds(domain).filter { onlySourceId == null || it == onlySourceId }
        if (ids.isEmpty()) return@withContext RefreshReport()

        var updated = 0
        var failed = 0
        var configs = 0
        ids.forEach { id ->
            val single = refreshSource(id)
            updated += single.updatedSources
            failed += single.failedSources
            configs += single.importedConfigs
        }
        RefreshReport(
            requestedSources = ids.size,
            updatedSources = updated,
            failedSources = failed,
            importedConfigs = configs,
        )
    }

    private fun refreshSource(sourceId: String): RefreshReport {
        val item = MmkvManager.decodeSubscription(sourceId)
            ?: return RefreshReport(requestedSources = 1, failedSources = 1)
        val result = runCatching {
            AngConfigManager.updateConfigViaSub(SubscriptionCache(sourceId, item))
        }.getOrNull()

        val success = result != null && result.successCount > 0
        MmkvManager.encodeSettings(sourceFailureKey(sourceId), !success)
        return if (success) {
            RefreshReport(
                requestedSources = 1,
                updatedSources = 1,
                importedConfigs = result?.configCount ?: 0,
            )
        } else {
            RefreshReport(requestedSources = 1, failedSources = 1)
        }
    }

    // ------------------------------------------------------------------
    // Manual import domain
    // ------------------------------------------------------------------

    /**
     * Imports user-supplied configuration text into the manual bucket.
     *
     * Subscription links are refused here: they belong to Home VPN or ExploreVPN sources, and
     * accepting one would silently move configurations across domains.
     */
    suspend fun importManualText(text: String?): ManualImportResult = withContext(Dispatchers.IO) {
        val content = text?.trim().orEmpty()
        if (content.isEmpty()) {
            return@withContext ManualImportResult(
                importedConfigs = 0,
                importedSubscriptions = 0,
                message = "Nothing to import",
                success = false,
            )
        }
        val subscriptionLine = content.lines().firstOrNull { Utils.isValidSubUrl(it.trim()) }
        if (subscriptionLine != null) {
            return@withContext ManualImportResult(
                importedConfigs = 0,
                importedSubscriptions = 0,
                message = "Subscription links belong to Home VPN or ExploreVPN sources",
                success = false,
            )
        }

        val result = runCatching {
            AngConfigManager.importBatchConfig(content, MANUAL_BUCKET, true)
        }.getOrNull()
        val configs = result?.first ?: 0
        val subscriptions = result?.second ?: 0
        if (configs <= 0) {
            return@withContext ManualImportResult(
                importedConfigs = 0,
                importedSubscriptions = 0,
                message = "No supported configuration was found in the input",
                success = false,
            )
        }
        ManualImportResult(
            importedConfigs = configs,
            importedSubscriptions = subscriptions,
            message = if (configs == 1) "1 configuration imported" else "$configs configurations imported",
            success = true,
        )
    }

    /** Renames a manually stored profile; allowed only inside the manual bucket. */
    fun renameManualProfile(guid: String, newName: String): Boolean {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return false
        if (profile.subscriptionId != MANUAL_BUCKET) return false
        profile.remarks = newName.trim().take(64)
        if (profile.remarks.isEmpty()) return false
        MmkvManager.encodeServerConfig(guid, profile)
        return true
    }

    /** Deletes a manually stored profile; allowed only inside the manual bucket. */
    fun removeManualProfile(guid: String): Boolean {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return false
        if (profile.subscriptionId != MANUAL_BUCKET) return false
        MmkvManager.removeServer(guid)
        if (selectedGuid(ConfigDomain.Manual) == guid) {
            MmkvManager.encodeSettings(selectedKey(ConfigDomain.Manual), "")
        }
        return true
    }

    fun isManualProfile(guid: String): Boolean =
        MmkvManager.decodeServerConfig(guid)?.subscriptionId == MANUAL_BUCKET

    // ------------------------------------------------------------------
    // Boost Security (real, verified engine setting only)
    // ------------------------------------------------------------------

    /**
     * "Boost Security" is wired to v2rayNG's local DNS interception preference
     * ([AppConfig.PREF_LOCAL_DNS_ENABLED]), which the core config builder actually consumes.
     * It is stored, not forced: the running tunnel keeps its current configuration until the
     * next connection, and the UI states that honestly.
     */
    fun isLocalDnsEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED, false)

    fun setLocalDnsEnabled(enabled: Boolean): Boolean =
        MmkvManager.encodeSettings(AppConfig.PREF_LOCAL_DNS_ENABLED, enabled)

    // ------------------------------------------------------------------
    // DNS Gaming
    // ------------------------------------------------------------------

    fun dnsProfiles(): List<DnsProfile> {
        val json = MmkvManager.decodeSettingsString(DNS_PROFILES) ?: return emptyList()
        return runCatching {
            JsonUtil.fromJsonSafe(json, Array<DnsProfile>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun storeDnsProfiles(profiles: List<DnsProfile>) {
        MmkvManager.encodeSettings(
            DNS_PROFILES,
            JsonUtil.toJson(profiles.toTypedArray())
        )
    }

    fun saveDnsProfile(profile: DnsProfile): Boolean {
        if (!isValidDnsAddress(profile.primary)) return false
        if (profile.secondary.isNotBlank() && !isValidDnsAddress(profile.secondary)) return false
        val current = dnsProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) current[index] = profile else current.add(0, profile)
        storeDnsProfiles(current)
        return true
    }

    fun removeDnsProfile(profileId: String) {
        storeDnsProfiles(dnsProfiles().filterNot { it.id == profileId })
        if (activeDnsProfileId() == profileId) {
            MmkvManager.encodeSettings(DNS_ACTIVE, "")
        }
    }

    fun activeDnsProfileId(): String? =
        MmkvManager.decodeSettingsString(DNS_ACTIVE)?.takeIf { it.isNotBlank() }

    /**
     * Applies a resolver pair through the existing DNS settings and records the active profile
     * only after both settings writes succeeded.
     */
    fun applyDns(profileId: String?, primary: String, secondary: String): Boolean {
        if (!isValidDnsAddress(primary)) return false
        if (secondary.isNotBlank() && !isValidDnsAddress(secondary)) return false
        val joined = listOf(primary, secondary).filter { it.isNotBlank() }.joinToString(",")

        val remoteOk = MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, joined)
        val vpnOk = MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, joined)
        return if (remoteOk && vpnOk) {
            MmkvManager.encodeSettings(DNS_ACTIVE, profileId.orEmpty())
            true
        } else {
            false
        }
    }

    fun currentRemoteDns(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS).orEmpty()

    fun currentVpnDns(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS).orEmpty()

    /** A plain IPv4/IPv6 resolver address is required; the engine settings accept nothing else. */
    fun isValidDnsAddress(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        return Utils.isPureIpAddress(trimmed)
    }

    // ------------------------------------------------------------------
    // Engine configuration helpers
    // ------------------------------------------------------------------

    /** Profile ids of one source, used for bounded ping batches. */
    fun guidsOf(sourceId: String): List<String> =
        MmkvManager.decodeServerList(sourceId).toList()

    /** Returns the fastest server with a real measured ping, if any. */
    suspend fun fastestMeasured(domain: ConfigDomain): SafeServer? =
        load(domain).servers
            .filter { it.hasMeasuredPing }
            .minByOrNull { it.pingMs ?: Long.MAX_VALUE }

    fun persistedPing(guid: String): Long? =
        MmkvManager.decodeServerAffiliationInfo(guid)
            ?.testDelayMillis
            ?.takeIf { it > 0L }

    /** Friendly name of the profile the engine is configured to use, sanitised for display. */
    fun activeServerLabel(): String {
        val guid = MmkvManager.getSelectServer() ?: return ""
        if (guid.isBlank()) return ""
        val profile = MmkvManager.decodeServerConfig(guid) ?: return ""
        return sanitizeName(profile.remarks).ifBlank { "Unnamed server" }
    }

    fun activeServerCountryCode(): String? {
        val guid = MmkvManager.getSelectServer() ?: return null
        if (guid.isBlank()) return null
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        return detectCountryCode(sanitizeName(profile.remarks))
    }

    fun hasAnyEngineSelection(): Boolean =
        !MmkvManager.getSelectServer().isNullOrBlank()
}
