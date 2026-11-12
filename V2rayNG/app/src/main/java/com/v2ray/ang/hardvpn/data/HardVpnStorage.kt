package com.v2ray.ang.hardvpn.data

import com.tencent.mmkv.MMKV
import com.v2ray.ang.hardvpn.domain.DnsProfile
import com.v2ray.ang.hardvpn.domain.DnsProfileList
import com.v2ray.ang.hardvpn.domain.HardVpnDomain
import com.v2ray.ang.util.JsonUtil

/** JSON container so the ordered source list round-trips without a generic Gson type token. */
internal data class SourceIdList(val ids: List<String> = emptyList())

/**
 * HardVPN-owned storage buckets.
 *
 * MMKV remains the project's persistence owner; this object only adds namespaces that HardVPN
 * owns so Home VPN, ExploreVPN, manual import, DNS Gaming, and app-level records never share a key
 * with v2rayNG's profile, subscription, or settings buckets. Server payloads themselves stay in
 * `MmkvManager` because the native engine reads them from there.
 *
 * The subscription IDs recorded here are the ownership boundary: a Home source can never appear
 * in ExploreVPN, and neither can appear in Manual Import.
 */
object HardVpnStorage {

    private const val ID_HOME = "HARDVPN_HOME"
    private const val ID_EXPLORE = "HARDVPN_EXPLORE"
    private const val ID_DNS = "HARDVPN_DNS"
    private const val ID_APP = "HARDVPN_APP"

    private const val KEY_SOURCE_IDS = "source_ids"
    private const val KEY_SELECTED_GUID = "selected_guid"
    private const val KEY_AUTO_SELECT = "auto_select"
    private const val KEY_DNS_PROFILES = "dns_profiles"
    private const val KEY_BOOST_BACKUP_VPN_DNS = "boost_backup_vpn_dns"
    private const val KEY_BOOST_BACKUP_REMOTE_DNS = "boost_backup_remote_dns"
    private const val KEY_MANUAL_SELECTED_GUID = "manual_selected_guid"

    private val homeStore by lazy { MMKV.mmkvWithID(ID_HOME) }
    private val exploreStore by lazy { MMKV.mmkvWithID(ID_EXPLORE) }
    private val dnsStore by lazy { MMKV.mmkvWithID(ID_DNS) }
    private val appStore by lazy { MMKV.mmkvWithID(ID_APP) }

    private fun domainStore(domain: HardVpnDomain): MMKV = when (domain) {
        HardVpnDomain.HOME -> homeStore
        HardVpnDomain.EXPLORE -> exploreStore
    }

    // ---------- Domain sources ----------

    fun sourceIds(domain: HardVpnDomain): List<String> {
        val raw = domainStore(domain).decodeString(KEY_SOURCE_IDS) ?: return emptyList()
        return JsonUtil.fromJsonSafe(raw, SourceIdList::class.java)?.ids.orEmpty()
    }

    fun setSourceIds(domain: HardVpnDomain, ids: List<String>) {
        domainStore(domain).encode(
            KEY_SOURCE_IDS,
            JsonUtil.toJson(SourceIdList(ids.distinct())),
        )
    }

    fun addSourceId(domain: HardVpnDomain, id: String) {
        val ids = sourceIds(domain)
        if (id in ids) return
        setSourceIds(domain, ids + id)
    }

    fun removeSourceId(domain: HardVpnDomain, id: String) {
        setSourceIds(domain, sourceIds(domain).filterNot { it == id })
    }

    // ---------- Domain selection ----------

    fun selectedGuid(domain: HardVpnDomain): String? =
        domainStore(domain).decodeString(KEY_SELECTED_GUID)?.takeIf { it.isNotBlank() }

    fun setSelectedGuid(domain: HardVpnDomain, guid: String?) {
        if (guid.isNullOrBlank()) {
            domainStore(domain).removeValueForKey(KEY_SELECTED_GUID)
        } else {
            domainStore(domain).encode(KEY_SELECTED_GUID, guid)
        }
    }

    fun autoSelect(domain: HardVpnDomain): Boolean =
        domainStore(domain).decodeBool(KEY_AUTO_SELECT, false)

    fun setAutoSelect(domain: HardVpnDomain, enabled: Boolean) {
        domainStore(domain).encode(KEY_AUTO_SELECT, enabled)
    }

    /** Manual Import selection is kept per destination, separate from Home and ExploreVPN. */
    fun selectedManualGuid(): String? =
        appStore.decodeString(KEY_MANUAL_SELECTED_GUID)?.takeIf { it.isNotBlank() }

    fun setSelectedManualGuid(guid: String?) {
        if (guid.isNullOrBlank()) appStore.removeValueForKey(KEY_MANUAL_SELECTED_GUID)
        else appStore.encode(KEY_MANUAL_SELECTED_GUID, guid)
    }

    // ---------- DNS profiles ----------

    fun dnsProfiles(): List<DnsProfile> {
        val raw = dnsStore.decodeString(KEY_DNS_PROFILES) ?: return emptyList()
        return JsonUtil.fromJsonSafe(raw, DnsProfileList::class.java)?.profiles.orEmpty()
    }

    fun saveDnsProfiles(profiles: List<DnsProfile>) {
        dnsStore.encode(KEY_DNS_PROFILES, JsonUtil.toJson(DnsProfileList(profiles)))
    }

    // ---------- Boost Security backup ----------

    fun saveBoostBackup(vpnDns: String?, remoteDns: String?) {
        if (vpnDns.isNullOrBlank()) appStore.removeValueForKey(KEY_BOOST_BACKUP_VPN_DNS)
        else appStore.encode(KEY_BOOST_BACKUP_VPN_DNS, vpnDns)
        if (remoteDns.isNullOrBlank()) appStore.removeValueForKey(KEY_BOOST_BACKUP_REMOTE_DNS)
        else appStore.encode(KEY_BOOST_BACKUP_REMOTE_DNS, remoteDns)
    }

    fun boostBackupVpnDns(): String? =
        appStore.decodeString(KEY_BOOST_BACKUP_VPN_DNS)?.takeIf { it.isNotBlank() }

    fun boostBackupRemoteDns(): String? =
        appStore.decodeString(KEY_BOOST_BACKUP_REMOTE_DNS)?.takeIf { it.isNotBlank() }

    fun clearBoostBackup() {
        appStore.removeValueForKey(KEY_BOOST_BACKUP_VPN_DNS)
        appStore.removeValueForKey(KEY_BOOST_BACKUP_REMOTE_DNS)
    }
}
