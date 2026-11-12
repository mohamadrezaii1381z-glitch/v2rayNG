package com.v2ray.ang.hardvpn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.hardvpn.HardVpnAction
import com.v2ray.ang.hardvpn.HardVpnViewModel
import com.v2ray.ang.hardvpn.domain.HardVpnDomain
import com.v2ray.ang.hardvpn.domain.HardVpnTab

/**
 * HardVPN shell: exactly four destinations — Home, ExploreVPN, DNS Gaming, Import Config.
 *
 * Premium is a sheet opened from the Home header, never a navigation destination.
 */
@Composable
fun HardVpnRoot(
    viewModel: HardVpnViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showPremium by rememberSaveable { mutableStateOf(false) }
    var showHomeServers by rememberSaveable { mutableStateOf(false) }
    var showHomeSources by rememberSaveable { mutableStateOf(false) }
    var showExploreSources by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.textRes, *message.args.toTypedArray())
            )
        }
    }

    BackHandler(enabled = showHomeServers || showHomeSources || showExploreSources) {
        when {
            showHomeSources -> showHomeSources = false
            showExploreSources -> showExploreSources = false
            else -> showHomeServers = false
        }
    }

    HardVpnTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(HardVpnColors.Background)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    HardVpnBottomBar(
                        selected = state.tab,
                        onSelect = { tab ->
                            showHomeServers = false
                            viewModel.onAction(HardVpnAction.SelectTab(tab))
                        },
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    when (state.tab) {
                        HardVpnTab.HOME -> {
                            if (showHomeServers) {
                                HomeServersScreen(
                                    domain = state.home,
                                    connection = state.connection,
                                    onAction = viewModel::onAction,
                                    onBack = { showHomeServers = false },
                                    onManageSources = { showHomeSources = true },
                                )
                            } else {
                                HomeScreen(
                                    state = state,
                                    onAction = viewModel::onAction,
                                    onOpenPremium = { showPremium = true },
                                    onOpenServers = { showHomeServers = true },
                                )
                            }
                        }

                        HardVpnTab.EXPLORE -> ExploreScreen(
                            domain = state.explore,
                            connection = state.connection,
                            onAction = viewModel::onAction,
                            onManageSources = { showExploreSources = true },
                        )

                        HardVpnTab.DNS_GAMING -> DnsGamingScreen(
                            state = state.dns,
                            onAction = viewModel::onAction,
                        )

                        HardVpnTab.IMPORT_CONFIG -> ImportConfigScreen(
                            state = state,
                            onAction = viewModel::onAction,
                        )
                    }
                }
            }

            if (showPremium) {
                PremiumSheet(onDismiss = { showPremium = false })
            }
            if (showHomeSources) {
                SourcesSheet(
                    domain = HardVpnDomain.HOME,
                    sources = state.home.sources,
                    onDismiss = { showHomeSources = false },
                    onAction = viewModel::onAction,
                )
            }
            if (showExploreSources) {
                SourcesSheet(
                    domain = HardVpnDomain.EXPLORE,
                    sources = state.explore.sources,
                    onDismiss = { showExploreSources = false },
                    onAction = viewModel::onAction,
                )
            }
        }
    }
}
