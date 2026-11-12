package com.v2ray.ang.ui.hardvpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.hardvpn.data.HardVpnRepository
import com.v2ray.ang.ui.server.ProfileEditorResult
import com.v2ray.ang.ui.server.ServerCustomConfigActivity
import com.v2ray.ang.ui.server.ServerGroupActivity
import com.v2ray.ang.ui.server.ServerHttpActivity
import com.v2ray.ang.ui.server.ServerHysteria2Activity
import com.v2ray.ang.ui.server.ServerMasqueActivity
import com.v2ray.ang.ui.server.ServerProxyChainActivity
import com.v2ray.ang.ui.server.ServerShadowsocksActivity
import com.v2ray.ang.ui.server.ServerSocksActivity
import com.v2ray.ang.ui.server.ServerTrojanActivity
import com.v2ray.ang.ui.server.ServerVlessActivity
import com.v2ray.ang.ui.server.ServerVmessActivity
import com.v2ray.ang.ui.server.ServerWireguardActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.AppConfig
import kotlinx.coroutines.launch

/**
 * HardVPN launcher activity.
 *
 * It owns the Android-specific parts the Compose UI must not know about: the VpnService
 * permission handshake, QR/file/clipboard import and the existing v2rayNG profile editors.
 * All connection state still comes from [HardVpnViewModel], which is fed by the daemon.
 */
class HardVpnActivity : HelperBaseComponentActivity() {

    private val viewModel: HardVpnViewModel by viewModels()

    private var pendingManualEntry = false

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                viewModel.startEngine()
            } else {
                viewModel.cancelPendingConnection()
            }
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                pendingManualEntry = false
                return@registerForActivityResult
            }
            val data = result.data ?: run {
                pendingManualEntry = false
                return@registerForActivityResult
            }
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
            if (action == ProfileEditorResult.ACTION_SAVED) {
                val guid = data.getStringExtra(ProfileEditorResult.EXTRA_GUID)
                if (pendingManualEntry && !guid.isNullOrBlank()) {
                    ensureManualBucket(guid)
                }
                val restartService = data.getBooleanExtra(
                    ProfileEditorResult.EXTRA_RESTART_SERVICE, false
                )
                if (restartService) LauncherManager.restartService(this)
            }
            pendingManualEntry = false
            viewModel.refreshAll()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDarkWindow()
    }

    override fun onResume() {
        super.onResume()
        applyDarkWindow()
        // Pick up changes made outside HardVPN (editors, subscription updates) without polling.
        viewModel.refreshAll()
    }

    private fun applyDarkWindow() {
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        HardVpnApp(
            viewModel = viewModel,
            onConnectRequest = { domain -> requestConnect(domain) },
            onDisconnect = { viewModel.disconnect() },
            onImportClipboard = { importClipboard() },
            onImportQr = { importQr() },
            onImportFile = { importFile() },
            onManualEntry = { type -> openManualEntry(type) },
            onEditManual = { guid -> openManualEditor(guid) },
        )
    }

    private fun requestConnect(domain: ConfigDomain) {
        lifecycleScope.launch {
            if (!viewModel.prepareConnection(domain)) return@launch
            if (!viewModel.isVpnMode()) {
                viewModel.startEngine()
                return@launch
            }
            val intent = VpnService.prepare(this@HardVpnActivity)
            if (intent == null) {
                viewModel.startEngine()
            } else {
                requestVpnPermission.launch(intent)
            }
        }
    }

    private fun importClipboard() {
        try {
            val text = Utils.getClipboard(this)
            viewModel.importText(text)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "HardVPN: failed to read clipboard", e)
            viewModel.showBanner("Clipboard is empty or unavailable", isError = true)
        }
    }

    private fun importQr() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                viewModel.importText(scanResult)
            }
        }
    }

    private fun importFile() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    viewModel.importText(reader.readText())
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "HardVPN: failed to read imported file", e)
                viewModel.showBanner("The selected file could not be read", isError = true)
            }
        }
    }

    private fun openManualEntry(type: EConfigType) {
        pendingManualEntry = true
        val activityClass = editorClassFor(type)
        val intent = Intent(this, activityClass).apply {
            putExtra("subscriptionId", HardVpnRepository.MANUAL_BUCKET)
            putExtra("createConfigType", type.value)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun openManualEditor(guid: String) {
        val profile = MmkvManager.decodeServerConfig(guid)
        if (profile == null) {
            viewModel.showBanner("This configuration is no longer available", isError = true)
            return
        }
        pendingManualEntry = false
        val activityClass = editorClassFor(profile.configType)
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", viewModel.uiState.value.isConnected)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", HardVpnRepository.MANUAL_BUCKET)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun editorClassFor(type: EConfigType): Class<*> = when (type) {
        EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
        EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
        EConfigType.VMESS -> ServerVmessActivity::class.java
        EConfigType.VLESS -> ServerVlessActivity::class.java
        EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
        EConfigType.SOCKS -> ServerSocksActivity::class.java
        EConfigType.HTTP -> ServerHttpActivity::class.java
        EConfigType.TROJAN -> ServerTrojanActivity::class.java
        EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
        EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
        EConfigType.MASQUE -> ServerMasqueActivity::class.java
        EConfigType.CUSTOM, EConfigType.CUSTOM_OUTBOUND -> ServerCustomConfigActivity::class.java
        else -> ServerHttpActivity::class.java
    }

    /**
     * The custom-config editor does not read the subscription extra, so a newly created custom
     * profile would land in the default bucket. Move it into the manual bucket so Manual Import
     * stays the single owner of hand-imported configurations.
     */
    private fun ensureManualBucket(guid: String) {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return
        if (profile.subscriptionId == HardVpnRepository.MANUAL_BUCKET) return
        MmkvManager.removeServer(guid)
        profile.subscriptionId = HardVpnRepository.MANUAL_BUCKET
        MmkvManager.encodeServerConfig(guid, profile)
    }
}
