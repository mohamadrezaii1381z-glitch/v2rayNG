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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.hardvpn.HardVpnAction
import com.v2ray.ang.hardvpn.domain.ConnectionPhase
import com.v2ray.ang.hardvpn.domain.ConnectionUiState
import com.v2ray.ang.hardvpn.domain.DomainUiState
import com.v2ray.ang.hardvpn.domain.HardVpnDomain
import com.v2ray.ang.hardvpn.domain.SourceUiModel
import java.text.DateFormat
import java.util.Date

/** The Home VPN server library: Auto Select, real ping values, search, and source management. */
@Composable
fun HomeServersScreen(
    domain: DomainUiState,
    connection: ConnectionUiState,
    onAction: (HardVpnAction) -> Unit,
    onBack: () -> Unit,
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
    HardVpnSecondaryBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.hardvpn_servers_title),
                subtitle = stringResource(
                    R.string.hardvpn_servers_subtitle,
                    domain.servers.size,
                    domain.sources.size,
                ),
                onBack = onBack,
                actions = {
                    IconAction(
                        icon = Icons.Filled.Refresh,
                        description = stringResource(R.string.hardvpn_refresh),
                        onClick = { onAction(HardVpnAction.RefreshDomain(HardVpnDomain.HOME)) },
                    )
                    IconAction(
                        icon = Icons.Filled.PlayArrow,
                        description = stringResource(R.string.hardvpn_test_all),
                        onClick = { onAction(HardVpnAction.TestDomain(HardVpnDomain.HOME)) },
                        enabled = !domain.isTesting && domain.servers.isNotEmpty(),
                    )
                    IconAction(
                        icon = Icons.Filled.Settings,
                        description = stringResource(R.string.hardvpn_manage_sources),
                        onClick = onManageSources,
                    )
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                HardVpnSearchField(
                    value = domain.query,
                    onValueChange = { onAction(HardVpnAction.UpdateQuery(HardVpnDomain.HOME, it)) },
                )
                Spacer(Modifier.height(10.dp))
                AutoSelectRow(
                    selected = domain.autoSelect,
                    onClick = { onAction(HardVpnAction.SelectAutoSelect(HardVpnDomain.HOME)) },
                )
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
                            title = stringResource(R.string.hardvpn_empty_sources_title),
                            body = stringResource(R.string.hardvpn_empty_sources_body),
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
                                onAction(HardVpnAction.RefreshDomain(HardVpnDomain.HOME))
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
                                onAction(HardVpnAction.SelectServer(HardVpnDomain.HOME, server.guid))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(
                icon = Icons.Filled.ArrowBack,
                description = stringResource(R.string.hardvpn_back),
                onClick = onBack,
            )
        } else {
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = HardVpnColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = HardVpnColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
    }
}

@Composable
fun BusyLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = HardVpnColors.AccentBright,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = HardVpnColors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
fun LoadingBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = HardVpnColors.AccentBright)
    }
}

/** Modal manager for one destination's sources. URLs are never displayed again after saving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(
    domain: HardVpnDomain,
    sources: List<SourceUiModel>,
    onDismiss: () -> Unit,
    onAction: (HardVpnAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SourceUiModel?>(null) }
    val title = if (domain == HardVpnDomain.HOME) {
        stringResource(R.string.hardvpn_home_sources_title)
    } else {
        stringResource(R.string.hardvpn_explore_sources_title)
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = HardVpnColors.SurfaceHigh,
            titleContentColor = HardVpnColors.TextPrimary,
            textContentColor = HardVpnColors.TextSecondary,
            title = { Text(stringResource(R.string.hardvpn_source_delete_title)) },
            text = {
                Text(stringResource(R.string.hardvpn_source_delete_body, target.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    onAction(HardVpnAction.DeleteSource(domain, target.id))
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.hardvpn_delete), color = HardVpnColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.hardvpn_cancel))
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HardVpnColors.Surface,
        contentColor = HardVpnColors.TextPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = title,
                color = HardVpnColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.hardvpn_source_url_hidden),
                color = HardVpnColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))

            sources.forEach { source ->
                SourceRow(
                    source = source,
                    onRefresh = { onAction(HardVpnAction.RefreshSource(domain, source.id)) },
                    onDelete = { pendingDelete = source },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (sources.isEmpty()) {
                Text(
                    text = stringResource(R.string.hardvpn_empty_sources_body),
                    color = HardVpnColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hardvpn_sources_add_title),
                color = HardVpnColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            HardVpnTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.hardvpn_source_name),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            HardVpnTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = stringResource(R.string.hardvpn_source_url),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                label = stringResource(R.string.hardvpn_source_add),
                enabled = url.isNotBlank(),
                onClick = {
                    onAction(HardVpnAction.AddSource(domain, name, url))
                    name = ""
                    url = ""
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceUiModel,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101F3C))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF172C55)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = HardVpnColors.AccentBright,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = source.name,
                color = HardVpnColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.hardvpn_source_servers, source.serverCount) +
                    " · " + formatUpdated(source.lastUpdatedMillis),
                color = HardVpnColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconAction(
            icon = Icons.Filled.Refresh,
            description = stringResource(R.string.hardvpn_source_refresh_a11y, source.name),
            onClick = onRefresh,
        )
        IconAction(
            icon = Icons.Filled.Delete,
            description = stringResource(R.string.hardvpn_source_delete_a11y, source.name),
            onClick = onDelete,
        )
    }
}

@Composable
private fun formatUpdated(lastUpdatedMillis: Long): String {
    if (lastUpdatedMillis <= 0L) return stringResource(R.string.hardvpn_source_never_updated)
    val formatted = remember(lastUpdatedMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(lastUpdatedMillis))
    }
    return stringResource(R.string.hardvpn_source_updated, formatted)
}

@Composable
fun HardVpnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text(placeholder, color = HardVpnColors.TextMuted, fontSize = 13.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HardVpnColors.Accent,
            unfocusedBorderColor = Color(0x336E8CBF),
            focusedContainerColor = Color(0xFF0B1730),
            unfocusedContainerColor = Color(0xFF0B1730),
            focusedTextColor = HardVpnColors.TextPrimary,
            unfocusedTextColor = HardVpnColors.TextPrimary,
            cursorColor = HardVpnColors.Accent,
        ),
    )
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = HardVpnColors.Accent,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else Color(0xFF22314F))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else HardVpnColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
