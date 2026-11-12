package com.v2ray.ang.hardvpn

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.hardvpn.data.HardVpnRepository
import com.v2ray.ang.hardvpn.ui.HardVpnRoot
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
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
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HardVPN entry point.
 *
 * The Activity only bridges Android capabilities the Compose layer cannot own: VPN consent, the
 * QR scanner, the document picker, profile editors, and the legacy v2rayNG tools. All state lives
 * in [HardVpnViewModel]; the engine is reached exclusively through [HardVpnRepository].
 */
class HardVpnActivity : HelperBaseComponentActivity() {

    private val viewModel: HardVpnViewModel by viewModels {
        HardVpnViewModel.Factory(
            application,
            HardVpnRepository(application as AngApplication),
        )
    }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.onVpnPermissionResult(result.resultCode == RESULT_OK)
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
                ?: return@registerForActivityResult
            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) {
                return@registerForActivityResult
            }
            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE, false
            )
            val selectedProfileChanged = action == ProfileEditorResult.ACTION_SAVED &&
                data.getStringExtra(ProfileEditorResult.EXTRA_GUID) ==
                MmkvManager.getSelectServer()
            viewModel.onManualEditorResult()
            if (restartService || selectedProfileChanged) LauncherManager.restartService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // HardVPN is dark-only; pin this activity to night mode before the theme is applied.
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        super.onCreate(savedInstanceState)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect(::handleEffect)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForegroundChanged(true)
    }

    override fun onStop() {
        viewModel.onForegroundChanged(false)
        super.onStop()
    }

    @Composable
    override fun ScreenContent() {
        HardVpnRoot(viewModel = viewModel)
    }

    private fun handleEffect(effect: HardVpnEffect) {
        when (effect) {
            HardVpnEffect.RequestVpnPermission -> requestServiceStart()
            HardVpnEffect.ReadClipboard -> importClipboard()
            HardVpnEffect.OpenQrScanner -> launchQRCodeScanner { scanned ->
                if (!scanned.isNullOrBlank()) viewModel.onAction(HardVpnAction.ImportText(scanned))
            }

            HardVpnEffect.OpenFilePicker -> launchFileChooser { uri ->
                if (uri != null) importFile(uri)
            }

            is HardVpnEffect.OpenManualEditor -> openProfileEditor(
                effect.guid,
                effect.configTypeValue,
            )

            HardVpnEffect.OpenLegacyTools ->
                startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun requestServiceStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        if (!SettingsManager.isVpnMode()) {
            // Proxy-only mode needs no tunnel consent.
            viewModel.onVpnPermissionResult(true)
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) {
            viewModel.onVpnPermissionResult(true)
        } else {
            vpnPermissionLauncher.launch(intent)
        }
    }

    private fun importClipboard() {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { Utils.getClipboard(this@HardVpnActivity) }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                toast(R.string.hardvpn_import_failed)
            } else {
                viewModel.onAction(HardVpnAction.ImportText(text))
            }
        }
    }

    private fun importFile(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                toast(R.string.hardvpn_import_failed)
            } else {
                viewModel.onAction(HardVpnAction.ImportText(text))
            }
        }
    }

    private fun openProfileEditor(guid: String, configTypeValue: Int) {
        val type = EConfigType.fromInt(configTypeValue) ?: EConfigType.VMESS
        val activityClass = when (type) {
            EConfigType.CUSTOM, EConfigType.CUSTOM_OUTBOUND -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2, EConfigType.HYSTERIA -> ServerHysteria2Activity::class.java
            EConfigType.MASQUE -> ServerMasqueActivity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("createConfigType", type.value)
            if (guid.isBlank()) putExtra("subscriptionId", HardVpnRepository.MANUAL_GROUP_ID)
            putExtra("isRunning", guid.isNotBlank() && guid == MmkvManager.getSelectServer())
        }
        profileEditorLauncher.launch(intent)
    }
}
