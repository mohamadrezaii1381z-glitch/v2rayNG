package com.v2ray.ang.ui.hardvpn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ExploreVPN: a completely independent configuration domain.
 *
 * It has its own sources, storage bucket, selection, search and refresh lifecycle. It never
 * reads the Home source index, and its rows are safe projections with no technical details.
 */
@Composable
fun ExploreVpnScreen(
    state: HardVpnUiState,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onManageSources: () -> Unit,
    onAddSource: () -> Unit,
    onConnectToggle: () -> Unit,
    onDestination: (HardVpnDestination) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortByPing by rememberSaveable { mutableStateOf(false) }
    val queryKey = remember(query) { searchKey(query.trim()) }
    val visible = remember(state.explore.servers, queryKey, sortByPing) {
        val filtered = if (queryKey.isEmpty()) state.explore.servers
        else state.explore.servers.filter { it.searchIndex.contains(queryKey) }
        if (sortByPing) {
            // Best real ping first; unmeasured servers stay at the end, never invented.
            filtered.sortedWith(
                compareBy<SafeServer> { !(it.hasMeasuredPing) }
                    .thenBy { it.pingMs ?: Long.MAX_VALUE }
            )
        } else filtered
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            val compact = maxHeight < 640.dp
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "ExploreVPN",
                    subtitle = "Independent servers · stored separately from Home",
                    action = {
                        // Telegram bot destination is not configured yet: shown disabled,
                        // with no invented username or URL behind it.
                        HeaderIcon(
                            icon = AppIcon.Send,
                            enabled = false,
                            onClick = {},
                        )
                        HeaderIcon(
                            icon = AppIcon.Refresh,
                            enabled = !state.refreshing,
                            onClick = onRefresh,
                        )
                        HeaderIcon(
                            icon = AppIcon.Bolt,
                            enabled = !state.pingRunning && state.explore.servers.isNotEmpty(),
                            onClick = onPing,
                        )
                        HeaderIcon(icon = AppIcon.Lock, onClick = onManageSources)
                    },
                )

                if (state.explore.servers.isEmpty()) {
                    EmptyState(
                        icon = AppIcon.Globe,
                        title = "No ExploreVPN source yet",
                        body = "ExploreVPN keeps its own sources and server list. Nothing from Home is reused here.",
                    )
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        PrimaryButton(
                            text = "Add ExploreVPN source",
                            onClick = onAddSource,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "explore-status") {
                            ExploreStatusStrip(
                                state = state,
                                onConnectToggle = onConnectToggle,
                            )
                        }
                        item(key = "explore-get-config") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(
                                    text = if (state.refreshing) "Getting configs…" else "Get Config",
                                    onClick = onRefresh,
                                    enabled = !state.refreshing,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Sort by ping (best first)",
                                        color = Muted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    PrimaryButton(
                                        text = if (sortByPing) "Ping ↓" else "Default",
                                        onClick = { sortByPing = !sortByPing },
                                        outlined = !sortByPing,
                                    )
                                }
                            }
                        }
                        item(key = "explore-search") {
                            SearchField(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = "Search ExploreVPN servers",
                            )
                        }
                        item(key = "explore-heading") {
                            SectionLabel(
                                text = if (queryKey.isEmpty()) {
                                    "${state.explore.servers.size} servers · ${state.explore.sources.size} source(s)"
                                } else {
                                    "${visible.size} result${if (visible.size == 1) "" else "s"}"
                                },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (visible.isEmpty()) {
                            item(key = "explore-empty-search") {
                                EmptyState(
                                    icon = AppIcon.Search,
                                    title = "No servers found",
                                    body = "Try another country, city, or server name.",
                                )
                            }
                        } else {
                            items(items = visible, key = { it.guid }) { server ->
                                ServerListRow(
                                    server = server,
                                    selected = server.guid == state.selectedExplore,
                                    showFlag = false,
                                    onClick = { onSelect(server.guid) },
                                )
                            }
                        }
                    }
                }

                HardVpnBottomNavigation(
                    selected = HardVpnDestination.ExploreVpn,
                    compact = compact,
                    onSelect = onDestination,
                )
            }
        }
    }
}

@Composable
private fun ExploreStatusStrip(state: HardVpnUiState, onConnectToggle: () -> Unit) {
    val connected = state.isConnected
    val accent = if (connected) HardGreen else LightBlue
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF122237),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF3E9CFF), Color(0xFF164A9F))),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                DrawIcon(AppIcon.Globe, Color.White, Modifier.size(20.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (state.phase) {
                        ConnectionPhase.Connected -> "Tunnel active"
                        ConnectionPhase.Connecting -> "Connecting…"
                        ConnectionPhase.Disconnecting -> "Disconnecting…"
                        ConnectionPhase.Error -> "Last attempt failed"
                        ConnectionPhase.Disconnected -> "Not connected"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = if (state.activeDomain == ConfigDomain.Explore) {
                        "Active source: ExploreVPN"
                    } else {
                        "Active source: ${state.activeSourceLabel}"
                    },
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PrimaryButton(
                text = when {
                    state.isConnected && state.activeDomain == ConfigDomain.Explore -> "Disconnect"
                    state.isConnected -> "Switch"
                    state.phase == ConnectionPhase.Connecting -> "Connecting…"
                    else -> "Connect"
                },
                onClick = onConnectToggle,
                enabled = !state.isBusy,
                outlined = !connected,
            )
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: AppIcon,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .hardClickable(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) {
        DrawIcon(icon, if (enabled) LightBlue else Muted.copy(alpha = 0.4f), Modifier.size(17.dp))
    }
}

private fun Modifier.hardClickable(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
