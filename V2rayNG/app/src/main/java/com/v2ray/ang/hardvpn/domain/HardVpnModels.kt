package com.v2ray.ang.hardvpn.domain

import androidx.annotation.StringRes

/** The four — and only four — bottom navigation destinations of HardVPN. */
enum class HardVpnTab { HOME, EXPLORE, DNS_GAMING, IMPORT_CONFIG }

/**
 * A VPN data domain with its own subscription ownership, selection, and refresh lifecycle.
 *
 * Manual import is not listed here because it owns profiles outside any subscription. The domains
 * share the single Android VPN tunnel but never share server lists or selection state.
 */
enum class HardVpnDomain(val key: String) {
    HOME("home"),
    EXPLORE("explore"),
}

/** Real connection lifecycle reported by the daemon, never inferred from a button press. */
enum class ConnectionPhase { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

/**
 * A server row that structurally cannot carry technical connection data.
 *
 * There is deliberately no host, port, UUID, transport, TLS, or raw-config field: the UI layer
 * physically cannot render a protected value from this model.
 */
data class ServerUiModel(
    val guid: String,
    val name: String,
    val flag: String? = null,
    val location: String? = null,
    val pingMillis: Long? = null,
    val isSelected: Boolean = false,
    val isActive: Boolean = false,
)

data class SourceUiModel(
    val id: String,
    val name: String,
    val serverCount: Int,
    val lastUpdatedMillis: Long,
)

data class DomainUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isTesting: Boolean = false,
    val servers: List<ServerUiModel> = emptyList(),
    val sources: List<SourceUiModel> = emptyList(),
    val query: String = "",
    val autoSelect: Boolean = false,
    val selectedGuid: String? = null,
) {
    val hasServers: Boolean get() = servers.isNotEmpty()
}

data class ConnectionUiState(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val activeGuid: String? = null,
    val activeServerName: String? = null,
    val connectedSinceMillis: Long? = null,
    val downloadBytesPerSecond: Long = 0,
    val uploadBytesPerSecond: Long = 0,
    val downloadedBytes: Long = 0,
    val uploadedBytes: Long = 0,
    val error: UiMessage? = null,
) {
    val isRunning: Boolean get() = phase == ConnectionPhase.CONNECTED
    val isBusy: Boolean
        get() = phase == ConnectionPhase.CONNECTING || phase == ConnectionPhase.DISCONNECTING
}

/**
 * A manually imported configuration. Technical details are allowed here only because the user
 * personally imported this profile through the Manual Import destination.
 */
data class ManualServerUiModel(
    val guid: String,
    val name: String,
    val summary: String? = null,
    val pingMillis: Long? = null,
    val isSelected: Boolean = false,
    val isActive: Boolean = false,
    val configTypeValue: Int = 0,
)

data class ManualUiState(
    val isLoading: Boolean = true,
    val isTesting: Boolean = false,
    val servers: List<ManualServerUiModel> = emptyList(),
    val query: String = "",
)

data class DnsProfile(
    val id: String,
    val name: String,
    val primary: String,
    val secondary: String? = null,
)

/** JSON container so profile lists round-trip through Gson without generic type tokens. */
data class DnsProfileList(val profiles: List<DnsProfile> = emptyList())

enum class DnsPreset { ELECTRO, CLOUDFLARE, CUSTOM }

data class DnsUiState(
    val appliedPrimary: String? = null,
    val appliedSecondary: String? = null,
    val preset: DnsPreset = DnsPreset.CUSTOM,
    val customPrimary: String = "",
    val customSecondary: String = "",
    val profiles: List<DnsProfile> = emptyList(),
    val requiresReconnect: Boolean = false,
) {
    val hasAppliedDns: Boolean get() = !appliedPrimary.isNullOrBlank()
}

/**
 * Boost Security reports only what the engine actually holds: whether the encrypted-DNS preset is
 * currently written to the settings the core reads. There is no simulated protection state.
 */
data class BoostSecurityUiState(
    val active: Boolean = false,
    val requiresReconnect: Boolean = false,
)

data class UiMessage(
    @StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
)

/** Immutable snapshot consumed by every HardVPN composable. */
data class HardVpnUiState(
    val tab: HardVpnTab = HardVpnTab.HOME,
    val connection: ConnectionUiState = ConnectionUiState(),
    val home: DomainUiState = DomainUiState(),
    val explore: DomainUiState = DomainUiState(),
    val manual: ManualUiState = ManualUiState(),
    val dns: DnsUiState = DnsUiState(),
    val boost: BoostSecurityUiState = BoostSecurityUiState(),
)
