package com.v2ray.ang.ui.hardvpn

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.v2ray.ang.enums.EConfigType

/**
 * HardVPN navigation scaffold.
 *
 * The four bottom-navigation destinations are owned by [HardVpnViewModel.destination]. Picker,
 * source manager, Premium and Sponsors are full-screen overlays with their own back handling, so
 * the bottom navigation always shows exactly the four required destinations and nothing else.
 */
/** How long a temporary in-app feedback banner stays visible before auto-dismissing. */
private const val BANNER_AUTO_DISMISS_MS = 2_000L

@Composable
fun HardVpnApp(
    viewModel: HardVpnViewModel,
    onConnectRequest: (ConfigDomain) -> Unit,
    onDisconnect: () -> Unit,
    onImportClipboard: () -> Unit,
    onManualEntry: (EConfigType) -> Unit,
    onEditManual: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    // Lifecycle-gated traffic sampling: the daemon is only queried while the UI is resumed.
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    var isResumed by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var addSourceDomain by rememberSaveable { mutableStateOf<ConfigDomain?>(null) }

    BackHandler(enabled = overlay != null) { overlay = null }

    fun handleConnectToggle(domain: ConfigDomain) {
        when {
            // Same owner: disconnect. Different owner: the centralized coordinator stops
            // the current connection, confirms termination, then starts the new one.
            state.isConnected && state.activeDomain == domain -> onDisconnect()
            state.phase == ConnectionPhase.Connecting -> viewModel.cancelConnection()
            state.phase == ConnectionPhase.Disconnecting -> Unit
            else -> onConnectRequest(domain)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (overlay) {
            "homePicker" -> ServerPickerScreen(
                domain = ConfigDomain.Home,
                servers = state.home.servers,
                selectedGuid = state.selectedHome,
                autoSelect = state.autoSelect,
                loading = state.homeLoading,
                refreshing = state.refreshing,
                pingRunning = state.pingRunning,
                onSelect = {
                    viewModel.selectServer(ConfigDomain.Home, it)
                    overlay = null
                },
                onSelectAuto = {
                    viewModel.selectAutoMode()
                    overlay = null
                },
                onRefresh = { viewModel.refreshDomain(ConfigDomain.Home) },
                onPing = { viewModel.pingDomain(ConfigDomain.Home) },
                onManageSources = { overlay = "homeSources" },
                onAddSource = { addSourceDomain = ConfigDomain.Home },
                onBack = { overlay = null },
            )

            "homeSources" -> SourceManagerScreen(
                domain = ConfigDomain.Home,
                sources = state.home.sources,
                refreshing = state.refreshing,
                onBack = { overlay = "homePicker" },
                onAddSource = { addSourceDomain = ConfigDomain.Home },
                onRefreshAll = { viewModel.refreshDomain(ConfigDomain.Home) },
                onRefreshSource = { viewModel.refreshSource(ConfigDomain.Home, it) },
                onRemoveSource = { viewModel.removeSource(ConfigDomain.Home, it) },
            )

            "exploreSources" -> SourceManagerScreen(
                domain = ConfigDomain.Explore,
                sources = state.explore.sources,
                refreshing = state.refreshing,
                onBack = { overlay = null },
                onAddSource = { addSourceDomain = ConfigDomain.Explore },
                onRefreshAll = { viewModel.refreshDomain(ConfigDomain.Explore) },
                onRefreshSource = { viewModel.refreshSource(ConfigDomain.Explore, it) },
                onRemoveSource = { viewModel.removeSource(ConfigDomain.Explore, it) },
            )

            "premium" -> PremiumScreen(onBack = { overlay = null })

            "sponsors" -> SponsorsScreen(onBack = { overlay = null })

            else -> when (state.destination) {
                HardVpnDestination.Home -> HomeScreen(
                    state = state,
                    isResumed = isResumed,
                    onToggleConnection = { handleConnectToggle(ConfigDomain.Home) },
                    onPremiumClick = { overlay = "premium" },
                    onServerCardClick = { overlay = "homePicker" },
                    onSponsorsClick = { overlay = "sponsors" },
                    onSampleTraffic = { viewModel.sampleTraffic() },
                    onDestination = { viewModel.onDestinationSelected(it) },
                    onRefreshHome = { viewModel.refreshDomain(ConfigDomain.Home) },
                )

                HardVpnDestination.ExploreVpn -> ExploreVpnScreen(
                    state = state,
                    onSelect = { viewModel.selectServer(ConfigDomain.Explore, it) },
                    onRefresh = { viewModel.refreshDomain(ConfigDomain.Explore) },
                    onPing = { viewModel.pingDomain(ConfigDomain.Explore) },
                    onManageSources = { overlay = "exploreSources" },
                    onAddSource = { addSourceDomain = ConfigDomain.Explore },
                    onConnectToggle = { handleConnectToggle(ConfigDomain.Explore) },
                    onDestination = { viewModel.onDestinationSelected(it) },
                )

                HardVpnDestination.DnsGaming -> DnsGamingScreen(
                    state = state,
                    onApplyPreset = { name, primary, secondary ->
                        viewModel.applyDnsPreset(name, primary, secondary)
                    },
                    onApplyProfile = { viewModel.applyDnsProfile(it) },
                    onSaveProfile = { viewModel.saveDnsProfile(it) },
                    onDeleteProfile = { viewModel.deleteDnsProfile(it) },
                    onDeactivateDns = { viewModel.deactivateDns() },
                    onDestination = { viewModel.onDestinationSelected(it) },
                )

                HardVpnDestination.ImportConfig -> ImportConfigScreen(
                    state = state,
                    onSelect = { viewModel.selectServer(ConfigDomain.Manual, it) },
                    onConnectToggle = { handleConnectToggle(ConfigDomain.Manual) },
                    onImportText = { viewModel.importText(it) },
                    onImportClipboard = onImportClipboard,
                    onRefreshSource = { viewModel.refreshManualSource(it) },
                    onRemoveSource = { viewModel.removeManualSource(it) },
                    onManualEntry = onManualEntry,
                    onEdit = onEditManual,
                    onRename = { guid, name -> viewModel.renameManual(guid, name) },
                    onDelete = { viewModel.deleteManual(it) },
                    onPing = { viewModel.pingManualServers() },
                    onDestination = { viewModel.onDestinationSelected(it) },
                )
            }
        }

        // Transient, honest feedback. Every banner auto-dismisses after ~2 seconds;
        // a newer banner cancels the previous banner's dismissal timer because the
        // effect is keyed by banner id. Banners never contain configuration data.
        val banner = state.banner
        LaunchedEffect(banner?.id) {
            if (banner != null) {
                kotlinx.coroutines.delay(BANNER_AUTO_DISMISS_MS)
                viewModel.dismissBanner(banner.id)
            }
        }
        AnimatedVisibility(
            visible = banner != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 8.dp),
        ) {
            if (banner != null) {
                Column {
                    InfoBanner(
                        message = banner.message,
                        isError = banner.isError,
                        onDismiss = { viewModel.dismissBanner(banner.id) },
                    )
                }
            }
        }
    }

    addSourceDomain?.let { domain ->
        AddSourceDialog(
            domain = domain,
            onDismiss = { addSourceDomain = null },
            onConfirm = { name, url ->
                addSourceDomain = null
                viewModel.addSource(domain, name, url)
            },
        )
    }
}
