package com.v2ray.ang.hardvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.hardvpn.HardVpnAction
import com.v2ray.ang.hardvpn.domain.ConnectionPhase
import com.v2ray.ang.hardvpn.domain.ConnectionUiState
import com.v2ray.ang.hardvpn.domain.DomainUiState
import com.v2ray.ang.hardvpn.domain.HardVpnDomain

/**
 * ExploreVPN: an independent server library with its own sources, selection, and refresh state.
 * It never reads Home VPN's sources and never exposes technical configuration values.
 */
@Composable
fun ExploreScreen(
    domain: DomainUiState,
    connection: ConnectionUiState,
    onAction: (HardVpnAction) -> Unit,
    onManageSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(domain.servers, domain.query) {
        val query = domain.query.trim()
        if (query.isEmpty()) domain.servers
        else domain.servers.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.location?.contains(query, ignoreCase = true) == true
        }
    }
    val selected = domain.servers.firstOrNull { it.guid == domain.selectedGuid }
    val subtitle = selected?.name ?: stringResource(R.string.hardvpn_explore_select_hint)

    HardVpnSecondaryBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.hardvpn_tab_explore),
                subtitle = stringResource(
                    R.string.hardvpn_servers_subtitle,
                    domain.servers.size,
                    domain.sources.size,
                ),
                onBack = null,
                actions = {
                    IconAction(
                        icon = Icons.Filled.Add,
                        description = stringResource(R.string.hardvpn_sources_add_title),
                        onClick = onManageSources,
                    )
                    IconAction(
                        icon = Icons.Filled.Refresh,
                        description = stringResource(R.string.hardvpn_refresh),
                        onClick = { onAction(HardVpnAction.RefreshDomain(HardVpnDomain.EXPLORE)) },
                    )
                    IconAction(
                        icon = Icons.Filled.Settings,
                        description = stringResource(R.string.hardvpn_manage_sources),
                        onClick = onManageSources,
                    )
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.hardvpn_explore_subtitle),
                    color = HardVpnColors.TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                ConnectBar(
                    phase = connection.phase,
                    subtitle = subtitle,
                    onToggle = { onAction(HardVpnAction.ToggleConnection) },
                )
                Spacer(Modifier.height(10.dp))
                HardVpnSearchField(
                    value = domain.query,
                    onValueChange = {
                        onAction(HardVpnAction.UpdateQuery(HardVpnDomain.EXPLORE, it))
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InlineAction(
                        label = stringResource(R.string.hardvpn_test_latency),
                        icon = Icons.Filled.PlayArrow,
                        onClick = { onAction(HardVpnAction.TestDomain(HardVpnDomain.EXPLORE)) },
                    )
                    InlineAction(
                        label = stringResource(R.string.hardvpn_refresh),
                        icon = Icons.Filled.Refresh,
                        onClick = { onAction(HardVpnAction.RefreshDomain(HardVpnDomain.EXPLORE)) },
                    )
                }
                if (domain.isTesting || domain.isRefreshing) {
                    Spacer(Modifier.height(8.dp))
                    BusyLine(
                        text = stringResource(
                            if (domain.isTesting) R.string.hardvpn_testing
                            else R.string.hardvpn_refreshing
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                domain.isLoading -> LoadingBlock()
                domain.servers.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    if (domain.sources.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Add,
                            title = stringResource(R.string.hardvpn_explore_empty_title),
                            body = stringResource(R.string.hardvpn_explore_empty_body),
                            actionLabel = stringResource(R.string.hardvpn_source_add),
                            onAction = onManageSources,
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Filled.Refresh,
                            title = stringResource(R.string.hardvpn_empty_servers_title),
                            body = stringResource(R.string.hardvpn_empty_servers_body),
                            actionLabel = stringResource(R.string.hardvpn_refresh),
                            onAction = {
                                onAction(HardVpnAction.RefreshDomain(HardVpnDomain.EXPLORE))
                            },
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = filtered, key = { it.guid }) { server ->
                        ServerRow(
                            server = server,
                            selected = server.guid == domain.selectedGuid,
                            active = connection.phase == ConnectionPhase.CONNECTED &&
                                server.guid == connection.activeGuid,
                            onClick = {
                                onAction(
                                    HardVpnAction.SelectServer(HardVpnDomain.EXPLORE, server.guid)
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InlineAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101F3C))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HardVpnColors.AccentBright,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = HardVpnColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
