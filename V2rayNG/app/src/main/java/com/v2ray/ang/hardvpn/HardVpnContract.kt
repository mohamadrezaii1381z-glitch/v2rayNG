package com.v2ray.ang.hardvpn

import com.v2ray.ang.hardvpn.domain.DnsPreset
import com.v2ray.ang.hardvpn.domain.HardVpnDomain
import com.v2ray.ang.hardvpn.domain.HardVpnTab

/**
 * Every user operation that changes HardVPN state or talks to the engine.
 *
 * Composables only dispatch these actions; they never touch repositories, MMKV, or settings.
 */
sealed interface HardVpnAction {

    data class SelectTab(val tab: HardVpnTab) : HardVpnAction

    /** Connects or disconnects using the selection of the active destination. */
    data object ToggleConnection : HardVpnAction

    data class SelectServer(val domain: HardVpnDomain, val guid: String) : HardVpnAction

    data class SelectAutoSelect(val domain: HardVpnDomain) : HardVpnAction

    data class RefreshDomain(val domain: HardVpnDomain) : HardVpnAction

    data class RefreshSource(val domain: HardVpnDomain, val sourceId: String) : HardVpnAction

    data class AddSource(val domain: HardVpnDomain, val name: String, val url: String) : HardVpnAction

    data class RenameSource(
        val domain: HardVpnDomain,
        val sourceId: String,
        val name: String,
    ) : HardVpnAction

    data class DeleteSource(val domain: HardVpnDomain, val sourceId: String) : HardVpnAction

    data class UpdateQuery(val domain: HardVpnDomain, val query: String) : HardVpnAction

    data class UpdateManualQuery(val query: String) : HardVpnAction

    data class TestDomain(val domain: HardVpnDomain) : HardVpnAction

    data object TestManual : HardVpnAction

    /** Pasted text, a scanned code, or a loaded file, imported into Manual Import only. */
    data class ImportText(val text: String) : HardVpnAction

    data object RequestClipboardImport : HardVpnAction

    data object RequestQrImport : HardVpnAction

    data object RequestFileImport : HardVpnAction

    data class SelectManual(val guid: String) : HardVpnAction

    data class ConnectManual(val guid: String) : HardVpnAction

    data class RenameManual(val guid: String, val name: String) : HardVpnAction

    data class DeleteManual(val guid: String) : HardVpnAction

    data class EditManual(val guid: String, val configTypeValue: Int) : HardVpnAction

    /** Opens the existing v2rayNG editor for a new configuration of the given type. */
    data class AddManual(val configTypeValue: Int) : HardVpnAction

    data object OpenLegacyTools : HardVpnAction

    data class DnsApplyPreset(val preset: DnsPreset) : HardVpnAction

    data class DnsApplyCustom(val primary: String, val secondary: String) : HardVpnAction

    data class DnsSaveProfile(
        val id: String?,
        val name: String,
        val primary: String,
        val secondary: String,
    ) : HardVpnAction

    data class DnsApplyProfile(val id: String) : HardVpnAction

    data class DnsDeleteProfile(val id: String) : HardVpnAction

    data object RefreshDns : HardVpnAction

    data object ToggleBoostSecurity : HardVpnAction
}

/** One-shot requests that only the Activity can fulfil (permissions, pickers, editors). */
sealed interface HardVpnEffect {
    data object RequestVpnPermission : HardVpnEffect
    data object ReadClipboard : HardVpnEffect
    data object OpenQrScanner : HardVpnEffect
    data object OpenFilePicker : HardVpnEffect
    data class OpenManualEditor(val guid: String, val configTypeValue: Int) : HardVpnEffect
    data object OpenLegacyTools : HardVpnEffect
}
