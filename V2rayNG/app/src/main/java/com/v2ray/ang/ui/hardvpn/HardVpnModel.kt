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
    "AE" to "United Arab Emirates", "AM" to "Armenia", "AR" to "Argentina",
    "AT" to "Austria", "EG" to "Egypt",
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

/** Country-name aliases accepted at the very beginning of a configuration name. */
private val COUNTRY_PREFIX_ALIASES: Map<String, String> = mapOf(
    "germany" to "DE", "france" to "FR", "united states" to "US", "usa" to "US",
    "us" to "US", "italy" to "IT", "turkey" to "TR", "türkiye" to "TR",
    "turkiye" to "TR", "netherlands" to "NL", "united kingdom" to "GB", "uk" to "GB",
    "canada" to "CA", "japan" to "JP", "singapore" to "SG", "poland" to "PL",
    "sweden" to "SE", "finland" to "FI", "russia" to "RU", "ukraine" to "UA",
    "india" to "IN", "brazil" to "BR", "switzerland" to "CH", "spain" to "ES",
    "denmark" to "DK", "norway" to "NO", "austria" to "AT", "belgium" to "BE",
    "australia" to "AU", "ireland" to "IE", "portugal" to "PT", "greece" to "GR",
    "czechia" to "CZ", "romania" to "RO", "hungary" to "HU", "south korea" to "KR",
    "korea" to "KR", "china" to "CN", "taiwan" to "TW", "hong kong" to "HK",
    "indonesia" to "ID", "malaysia" to "MY", "thailand" to "TH", "vietnam" to "VN",
    "philippines" to "PH", "new zealand" to "NZ", "mexico" to "MX",
    "argentina" to "AR", "south africa" to "ZA", "egypt" to "EG", "israel" to "IL",
    "united arab emirates" to "AE", "saudi arabia" to "SA", "qatar" to "QA",
    "kazakhstan" to "KZ", "uzbekistan" to "UZ", "azerbaijan" to "AZ",
    "armenia" to "AM", "lithuania" to "LT", "latvia" to "LV", "estonia" to "EE",
    "slovakia" to "SK", "serbia" to "RS", "moldova" to "MD", "iran" to "IR",
)

/**
 * Best-effort, privacy-safe location label derived from a friendly remark.
 *
 * Prefix matching only: a flag emoji, country name, alias or two-letter code is honoured
 * only at the very beginning of the name (after optional leading symbols/brackets), so a
 * country word in the middle of an unrelated remark can never produce a false location.
 * Nothing is inferred from network metadata and no address is read.
 */
fun detectCountryCode(remarks: String): String? {
    val text = remarks.trim()
    if (text.isEmpty()) return null

    // Regional indicator pair (a flag emoji such as 🇩🇪) at the start.
    val emoji = text.codePoints().toArray().toList()
    if (emoji.size >= 2) {
        val first = emoji[0] - 0x1F1E6
        val second = emoji[1] - 0x1F1E6
        if (first in 0..25 && second in 0..25) {
            val code = "${('A' + first)}${('A' + second)}"
            if (KNOWN_COUNTRY_CODES.contains(code)) return normalizeCountryCode(code)
        }
    }

    // Strip leading separators/brackets, then require a prefix match.
    val head = text.trimStart { !it.isLetter() }
    if (head.isEmpty()) return null
    val lowerHead = head.lowercase(Locale.ROOT)

    // Country name / alias at the beginning, longest first ("united kingdom" beats "uk").
    COUNTRY_PREFIX_ALIASES.entries
        .sortedByDescending { it.key.length }
        .forEach { (alias, code) ->
            if (lowerHead == alias ||
                lowerHead.startsWith("$alias ") ||
                lowerHead.startsWith("$alias-") ||
                lowerHead.startsWith("$alias_") ||
                lowerHead.startsWith("$alias|") ||
                lowerHead.startsWith("$alias:") ||
                lowerHead.startsWith("$alias[") ||
                lowerHead.startsWith("$alias(")
            ) return code
        }

    // Official display names at the beginning (e.g. "Türkiye 01").
    KNOWN_COUNTRY_NAMES.entries
        .sortedByDescending { it.value.length }
        .forEach { (code, name) ->
            val lowerName = name.lowercase(Locale.ROOT)
            if (lowerHead == lowerName || lowerHead.startsWith("$lowerName ") ||
                lowerHead.startsWith("$lowerName-") || lowerHead.startsWith("$lowerName_")
            ) return code
        }

    // A two-letter code as the first token only, e.g. "DE-01", "[NL] fast", "us east".
    val firstToken = head.takeWhile { it.isLetter() }
    if (firstToken.length == 2) {
        val candidate = firstToken.uppercase(Locale.ROOT)
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
