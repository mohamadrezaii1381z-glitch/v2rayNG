package com.v2ray.ang.hardvpn.domain

import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Pure, Android-free decisions shared by the HardVPN repository and ViewModel.
 *
 * Keeping them here makes them JVM-testable and keeps policy out of composables.
 */
object SafeDisplayName {

    private val URL_REGEX = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")
    private val IPV4_REGEX = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b""")
    private val UUID_REGEX = Regex(
        """\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"""
    )
    private val HOST_PORT_REGEX = Regex("""\b[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}:\d{1,5}\b""")
    private val LONG_TOKEN_REGEX = Regex("""\b[A-Za-z0-9+/=_-]{32,}\b""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private const val MAX_LENGTH = 48

    /**
     * Returns a display name that cannot carry a protected endpoint or credential.
     *
     * The remark is author-controlled data, so it is treated as untrusted: URLs, IPv4 literals,
     * UUIDs, domain:port pairs, and long encoded tokens are removed. When nothing safe remains the
     * caller supplies a localized fallback, which is why this returns null instead of inventing
     * English text.
     */
    fun sanitize(raw: String?): String? {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) return null
        var cleaned = source
            .replace(URL_REGEX, " ")
            .replace(IPV4_REGEX, " ")
            .replace(UUID_REGEX, " ")
            .replace(HOST_PORT_REGEX, " ")
            .replace(LONG_TOKEN_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .trim('-', '|', '·', ':', '/', '(', ')')
            .trim()
        if (cleaned.isEmpty()) return null
        // A leftover "host:port" fragment has no letters before the colon but is still technical.
        cleaned = cleaned.replace(Regex("""\b:[0-9]{2,5}\b"""), " ").trim()
        if (cleaned.isEmpty() || cleaned.length < 2) return null
        if (cleaned.length > MAX_LENGTH) cleaned = cleaned.take(MAX_LENGTH).trim()
        return cleaned
    }
}

/** A country hint derived only from the user-visible server name. */
data class CountryHint(val flag: String, val name: String)

/**
 * Maps country names and ISO codes that appear in a server's display name to a flag and a stable
 * proper noun. This never inspects an address, so it cannot leak or infer technical data, and the
 * Home background never changes with it.
 */
object CountryHints {

    private class Entry(val flag: String, val name: String, vararg val aliases: String)

    private val entries = listOf(
        Entry("🇩🇪", "Germany", "germany", "deutschland", "frankfurt", "berlin", "DE"),
        Entry("🇺🇸", "United States", "united states", "usa", "america", "seattle", "new york", "los angeles", "US"),
        Entry("🇬🇧", "United Kingdom", "united kingdom", "england", "britain", "london", "UK", "GB"),
        Entry("🇳🇱", "Netherlands", "netherlands", "holland", "amsterdam", "NL"),
        Entry("🇫🇷", "France", "france", "paris", "FR"),
        Entry("🇨🇦", "Canada", "canada", "toronto", "montreal", "CA"),
        Entry("🇯🇵", "Japan", "japan", "tokyo", "osaka", "JP"),
        Entry("🇸🇬", "Singapore", "singapore", "SG"),
        Entry("🇭🇰", "Hong Kong", "hong kong", "hongkong", "HK"),
        Entry("🇹🇼", "Taiwan", "taiwan", "taipei", "TW"),
        Entry("🇰🇷", "South Korea", "south korea", "korea", "seoul", "KR"),
        Entry("🇮🇳", "India", "india", "mumbai", "delhi", "IN"),
        Entry("🇦🇺", "Australia", "australia", "sydney", "AU"),
        Entry("🇧🇷", "Brazil", "brazil", "sao paulo", "BR"),
        Entry("🇹🇷", "Türkiye", "turkey", "turkiye", "istanbul", "TR"),
        Entry("🇦🇪", "United Arab Emirates", "united arab emirates", "emirates", "dubai", "AE"),
        Entry("🇷🇺", "Russia", "russia", "moscow", "RU"),
        Entry("🇮🇷", "Iran", "iran", "tehran", "IR"),
        Entry("🇮🇹", "Italy", "italy", "milan", "rome", "IT"),
        Entry("🇪🇸", "Spain", "spain", "madrid", "barcelona", "ES"),
        Entry("🇸🇪", "Sweden", "sweden", "stockholm", "SE"),
        Entry("🇳🇴", "Norway", "norway", "oslo", "NO"),
        Entry("🇩🇰", "Denmark", "denmark", "copenhagen", "DK"),
        Entry("🇫🇮", "Finland", "finland", "helsinki", "FI"),
        Entry("🇵🇱", "Poland", "poland", "warsaw", "PL"),
        Entry("🇨🇭", "Switzerland", "switzerland", "zurich", "geneva", "CH"),
        Entry("🇦🇹", "Austria", "austria", "vienna", "AT"),
        Entry("🇧🇪", "Belgium", "belgium", "brussels", "BE"),
        Entry("🇮🇪", "Ireland", "ireland", "dublin", "IE"),
        Entry("🇵🇹", "Portugal", "portugal", "lisbon", "PT"),
        Entry("🇨🇿", "Czechia", "czechia", "czech republic", "prague", "CZ"),
        Entry("🇭🇺", "Hungary", "hungary", "budapest", "HU"),
        Entry("🇷🇴", "Romania", "romania", "bucharest", "RO"),
        Entry("🇬🇷", "Greece", "greece", "athens", "GR"),
        Entry("🇺🇦", "Ukraine", "ukraine", "kyiv", "kiev", "UA"),
        Entry("🇮🇱", "Israel", "israel", "tel aviv", "IL"),
        Entry("🇸🇦", "Saudi Arabia", "saudi arabia", "riyadh", "SA"),
        Entry("🇶🇦", "Qatar", "qatar", "doha", "QA"),
        Entry("🇰🇼", "Kuwait", "kuwait", "KW"),
        Entry("🇧🇭", "Bahrain", "bahrain", "BH"),
        Entry("🇴🇲", "Oman", "oman", "muscat", "OM"),
        Entry("🇯🇴", "Jordan", "jordan", "amman", "JO"),
        Entry("🇱🇧", "Lebanon", "lebanon", "beirut", "LB"),
        Entry("🇪🇬", "Egypt", "egypt", "cairo", "EG"),
        Entry("🇿🇦", "South Africa", "south africa", "johannesburg", "ZA"),
        Entry("🇳🇬", "Nigeria", "nigeria", "lagos", "NG"),
        Entry("🇲🇽", "Mexico", "mexico", "MX"),
        Entry("🇦🇷", "Argentina", "argentina", "buenos aires", "AR"),
        Entry("🇨🇱", "Chile", "chile", "santiago", "CL"),
        Entry("🇨🇴", "Colombia", "colombia", "bogota", "CO"),
        Entry("🇲🇾", "Malaysia", "malaysia", "kuala lumpur", "MY"),
        Entry("🇮🇩", "Indonesia", "indonesia", "jakarta", "ID"),
        Entry("🇹🇭", "Thailand", "thailand", "bangkok", "TH"),
        Entry("🇻🇳", "Vietnam", "vietnam", "hanoi", "VN"),
        Entry("🇵🇭", "Philippines", "philippines", "manila", "PH"),
        Entry("🇵🇰", "Pakistan", "pakistan", "karachi", "PK"),
        Entry("🇧🇩", "Bangladesh", "bangladesh", "dhaka", "BD"),
        Entry("🇰🇿", "Kazakhstan", "kazakhstan", "almaty", "KZ"),
        Entry("🇦🇲", "Armenia", "armenia", "yerevan", "AM"),
        Entry("🇬🇪", "Georgia", "georgia", "tbilisi", "GE"),
        Entry("🇦🇿", "Azerbaijan", "azerbaijan", "baku", "AZ"),
        Entry("🇳🇿", "New Zealand", "new zealand", "auckland", "NZ"),
    )

    /**
     * Resolves the first country mentioned by name, ISO code, or leading flag emoji.
     *
     * @param displayName a sanitized, user-visible server name.
     */
    fun resolve(displayName: String?): CountryHint? {
        if (displayName.isNullOrBlank()) return null
        entries.forEach { entry ->
            if (displayName.contains(entry.flag)) {
                return CountryHint(entry.flag, entry.name)
            }
        }
        val tokens = Regex("""[A-Za-z]{2,}""").findAll(displayName).map { it.value }.toList()
        val upperTokens = tokens.filter { it.length == 2 && it.all { c -> c.isUpperCase() } }.toSet()
        entries.forEach { entry ->
            entry.aliases.forEach { alias ->
                if (alias.length == 2 && alias.all { it.isUpperCase() }) {
                    if (alias in upperTokens) return CountryHint(entry.flag, entry.name)
                } else if (containsWord(displayName, alias)) {
                    return CountryHint(entry.flag, entry.name)
                }
            }
        }
        return null
    }

    private fun containsWord(text: String, word: String): Boolean =
        Regex("""(?<![A-Za-z])${Regex.escape(word)}(?![A-Za-z])""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
}

/**
 * Chooses which servers Auto Select probes and which measured result wins.
 *
 * Latency is measured by the existing v2rayNG test service; cached values only decide the probe
 * order and act as a fallback when no fresh measurement is available.
 */
object AutoSelectPolicy {

    /** Bounds a single Auto Select run so hundreds of servers never trigger hundreds of probes. */
    const val MAX_PROBES = 24

    fun candidateGuids(servers: List<ServerUiModel>, limit: Int = MAX_PROBES): List<String> =
        servers
            .sortedWith(
                compareBy<ServerUiModel>(
                    { if (it.pingMillis != null && it.pingMillis >= 0) 0 else 1 },
                    { it.pingMillis ?: Long.MAX_VALUE },
                    { it.guid },
                )
            )
            .take(limit)
            .map { it.guid }

    /**
     * @param measured guid-to-delay from the test service, negative values mean "unreachable".
     * @param cached last persisted delays for candidates not measured in this run.
     * @return the fastest reachable guid, or null when nothing is reachable.
     */
    fun bestServer(
        measured: Map<String, Long>,
        cached: Map<String, Long> = emptyMap(),
    ): String? {
        val reachable = (cached + measured)
            .filterValues { it >= 0 }
        return reachable.minByOrNull { it.value }?.key
    }
}

/** Strict DNS literal validation; no name resolution and no DNS lookup happens here. */
object DnsAddressValidation {

    // matches() anchors the whole value, so no ^/$ anchors are needed here.
    private val IPV4 = Regex(
        """(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)"""
    )
    private val IPV6 = Regex(
        "(" +
            "(?:[0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|" +
            "(?:[0-9A-Fa-f]{1,4}:){1,7}:|" +
            "(?:[0-9A-Fa-f]{1,4}:){1,6}:[0-9A-Fa-f]{1,4}|" +
            "(?:[0-9A-Fa-f]{1,4}:){1,5}(?::[0-9A-Fa-f]{1,4}){1,2}|" +
            "(?:[0-9A-Fa-f]{1,4}:){1,4}(?::[0-9A-Fa-f]{1,4}){1,3}|" +
            "(?:[0-9A-Fa-f]{1,4}:){1,3}(?::[0-9A-Fa-f]{1,4}){1,4}|" +
            "(?:[0-9A-Fa-f]{1,4}:){1,2}(?::[0-9A-Fa-f]{1,4}){1,5}|" +
            "[0-9A-Fa-f]{1,4}:(?::[0-9A-Fa-f]{1,4}){1,6}|" +
            ":(?::[0-9A-Fa-f]{1,4}){1,7}|::" +
            ")"
    )

    fun isValidLiteral(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        return IPV4.matches(trimmed) || IPV6.matches(trimmed)
    }
}

/** Session and speed formatting shared by the Home screen and its JVM tests. */
object HardVpnFormat {

    private val SPEED_UNITS = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    private const val DIVISOR = 1024.0

    /** HH:MM:SS, with hours allowed to exceed 99 for long-running sessions. */
    fun duration(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val seconds = safeSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun speed(bytesPerSecond: Long): String {
        var value = bytesPerSecond.coerceAtLeast(0).toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < SPEED_UNITS.lastIndex) {
            value /= DIVISOR
            unit++
        }
        val pattern = if (unit == 0) "%.0f %s" else "%.1f %s"
        return String.format(Locale.US, pattern, value, SPEED_UNITS[unit])
    }

    /** Human-readable session volume, without a per-second suffix. */
    fun data(bytes: Long): String = speed(bytes).replace("/s", "")

    /** Deterministic, non-sensitive suffix for a name that had to be replaced. */
    fun fallbackSuffix(guid: String): Int =
        (guid.hashCode().absoluteValue % 900) + 100
}
