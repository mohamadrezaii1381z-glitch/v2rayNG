package com.v2ray.ang.ui.hardvpn

import java.util.Locale

/**
 * The four HardVPN destinations. There is deliberately no Premium destination:
 * Premium is reachable from the gold button on Home only.
 */
enum class HardVpnDestination(val title: String) {
    Home("Home"),
    ExploreVpn("ExploreVPN"),
    DnsGaming("DNS Gaming"),
    ImportConfig("Import Config"),
}

/**
 * Independent configuration domains. Each one owns its own sources, server list,
 * selection and lifecycle; the only shared component is the Android VPN service.
 */
enum class ConfigDomain(val storageTag: String, val displayName: String) {
    Home("home", "Home VPN"),
    Explore("explore", "ExploreVPN"),
    Manual("manual", "Manual import"),
}

/** Real engine states; every value is driven by the daemon, never by a timer. */
enum class ConnectionPhase {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
    Error,
}

/**
 * Safe server projection.
 *
 * This type is the *only* server shape ordinary HardVPN screens receive. It carries no
 * address, port, protocol, credentials or subscription URL, so a leak cannot happen by
 * rendering a screen, a menu, a dialog or an accessibility label by accident.
 * [guid] is v2rayNG's opaque profile id, [sourceId] is the owning subscription id.
 */
data class SafeServer(
    val guid: String,
    val displayName: String,
    val location: String,
    val countryCode: String?,
    val pingMs: Long?,
    val domain: ConfigDomain,
    val sourceId: String,
    val sourceName: String,
) {
    val searchIndex: String = searchKey(
        "$displayName $location ${countryCode.orEmpty()} $sourceName"
    )

    val hasMeasuredPing: Boolean get() = pingMs != null && pingMs > 0L
}

/** Source (subscription) projection: a name and a count, never the URL. */
data class SourceUi(
    val id: String,
    val name: String,
    val serverCount: Int,
    val lastUpdatedAt: Long,
    val failedLastRefresh: Boolean,
)

data class DomainSnapshot(
    val sources: List<SourceUi> = emptyList(),
    val servers: List<SafeServer> = emptyList(),
) {
    val isEmpty: Boolean get() = servers.isEmpty()
}

/**
 * Result of one refresh wave. [failureCount] is reported honestly per source and
 * never causes previously imported configurations to be discarded.
 */
data class RefreshReport(
    val requestedSources: Int = 0,
    val updatedSources: Int = 0,
    val failedSources: Int = 0,
    val importedConfigs: Int = 0,
) {
    val nothingConfigured: Boolean get() = requestedSources == 0
}

/** Outcome of a clipboard/file/paste import into the manual domain. */
data class ManualImportResult(
    val importedConfigs: Int,
    val importedSubscriptions: Int,
    val message: String,
    val success: Boolean,
)

/** DNS Gaming state. [activeProfileId] is only set after the settings write succeeded. */
data class DnsUiState(
    val profiles: List<DnsProfile> = emptyList(),
    val activeProfileId: String? = null,
    val remoteDns: String = "",
    val vpnDns: String = "",
    val runningTunnelNeedsReconnect: Boolean = false,
    val lastError: String? = null,
) {
    val activeProfile: DnsProfile? get() = profiles.firstOrNull { it.id == activeProfileId }
}

/**
 * A saved DNS profile. Only plain resolver addresses are accepted, because that is what
 * the existing settings/engine path validates and consumes.
 */
data class DnsProfile(
    val id: String,
    val name: String,
    val primary: String,
    val secondary: String = "",
) {
    val servers: List<String>
        get() = listOf(primary, secondary).filter { it.isNotBlank() }

    val summary: String get() = servers.joinToString(", ")
}

/** Preset identifiers shown on the DNS Gaming screen. */
enum class DnsPreset(val title: String, val subtitle: String) {
    Electro("Electro DNS", "Preset address is not verified in this build"),
    Cloudflare("Cloudflare DNS", "1.1.1.1 / 1.0.0.1"),
    Custom("Custom DNS", "Your own resolvers"),
}

/** ISO-3166-1 alpha-2 codes we can safely recognise inside a server remark. */
private val KNOWN_COUNTRY_CODES: Set<String> = setOf(
    "AE", "AL", "AM", "AR", "AT", "AU", "AZ", "BA", "BD", "BE", "BG", "BH", "BR", "BY",
    "CA", "CH", "CL", "CN", "CO", "CR", "CY", "CZ", "DE", "DK", "DZ", "EE", "EG", "ES",
    "FI", "FR", "GB", "GE", "GR", "HK", "HR", "HU", "ID", "IE", "IL", "IN", "IQ", "IR",
    "IS", "IT", "JO", "JP", "KE", "KG", "KH", "KR", "KW", "KZ", "LA", "LB", "LK", "LT",
    "LU", "LV", "MA", "MD", "MK", "MM", "MN", "MT", "MX", "MY", "NG", "NL", "NO", "NZ",
    "OM", "PA", "PE", "PH", "PK", "PL", "PT", "QA", "RO", "RS", "RU", "SA", "SE", "SG",
    "SI", "SK", "TH", "TN", "TR", "TW", "UA", "UK", "US", "UZ", "VE", "VN", "ZA",
)

private val KNOWN_COUNTRY_NAMES: Map<String, String> = mapOf(
    "AE" to "United Arab Emirates", "AM" to "Armenia", "AT" to "Austria",
    "AU" to "Australia", "AZ" to "Azerbaijan", "BE" to "Belgium", "BR" to "Brazil",
    "CA" to "Canada", "CH" to "Switzerland", "CN" to "China", "CZ" to "Czechia",
    "DE" to "Germany", "DK" to "Denmark", "EE" to "Estonia", "ES" to "Spain",
    "FI" to "Finland", "FR" to "France", "GB" to "United Kingdom", "GR" to "Greece",
    "HK" to "Hong Kong", "HU" to "Hungary", "ID" to "Indonesia", "IE" to "Ireland",
    "IL" to "Israel", "IN" to "India", "IR" to "Iran", "IT" to "Italy",
    "JP" to "Japan", "KR" to "South Korea", "KZ" to "Kazakhstan",
    "LT" to "Lithuania", "LU" to "Luxembourg", "LV" to "Latvia", "MD" to "Moldova",
    "MX" to "Mexico", "MY" to "Malaysia", "NL" to "Netherlands", "NO" to "Norway",
    "NZ" to "New Zealand", "PH" to "Philippines", "PL" to "Poland", "PT" to "Portugal",
    "QA" to "Qatar", "RO" to "Romania", "RS" to "Serbia", "RU" to "Russia",
    "SA" to "Saudi Arabia", "SE" to "Sweden", "SG" to "Singapore", "SK" to "Slovakia",
    "TH" to "Thailand", "TR" to "Türkiye", "TW" to "Taiwan", "UA" to "Ukraine",
    "US" to "United States", "UZ" to "Uzbekistan", "VN" to "Vietnam", "ZA" to "South Africa",
)

/**
 * Best-effort, privacy-safe location label derived from a friendly remark.
 *
 * Only a country code / country name / flag emoji already present in the remark is used;
 * nothing is inferred from network metadata and no address is read.
 */
fun detectCountryCode(remarks: String): String? {
    val text = remarks.trim()
    if (text.isEmpty()) return null

    // Regional indicator pair (a flag emoji such as 🇩🇪).
    val emoji = text.codePoints().toArray().toList()
    for (i in 0 until emoji.size - 1) {
        val first = emoji[i] - 0x1F1E6
        val second = emoji[i + 1] - 0x1F1E6
        if (first in 0..25 && second in 0..25) {
            val code = "${('A' + first)}${('A' + second)}"
            if (KNOWN_COUNTRY_CODES.contains(code)) return normalizeCountryCode(code)
        }
    }

    // Country name, longest first so "United Kingdom" wins over "United".
    KNOWN_COUNTRY_NAMES.entries
        .sortedByDescending { it.value.length }
        .forEach { (code, name) ->
            if (text.contains(name, ignoreCase = true)) return code
        }

    // An explicit two-letter token such as "DE-01", "us east" or "[NL]".
    Regex("(?<![A-Za-z])([A-Za-z]{2})(?![A-Za-z])").findAll(text).forEach { match ->
        val candidate = match.groupValues[1].uppercase(Locale.ROOT)
        if (KNOWN_COUNTRY_CODES.contains(candidate)) return normalizeCountryCode(candidate)
    }
    return null
}

private fun normalizeCountryCode(code: String): String =
    if (code == "UK") "GB" else code

fun countryDisplayName(code: String?): String? =
    code?.let { KNOWN_COUNTRY_NAMES[it] }
}

/** Locale-independent lower-case country code used for optional `flag_xx` drawables. */
fun flagDrawableName(countryCode: String?): String? {
    val code = countryCode?.lowercase(Locale.ROOT) ?: return null
    return if (code.length == 2 && code.all { it in 'a'..'z' }) "flag_$code" else null
}
