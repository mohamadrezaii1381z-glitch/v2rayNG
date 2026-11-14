package com.v2ray.ang.ui.hardvpn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Server list for one domain (Home or ExploreVPN).
 *
 * Search is local to this screen, so the two domains keep independent search state. Rows only
 * ever receive [SafeServer] projections, which cannot expose addresses, ports or credentials.
 */
@Composable
fun ServerPickerScreen(
    domain: ConfigDomain,
    servers: List<SafeServer>,
    selectedGuid: String?,
    autoSelect: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    pingRunning: Boolean,
    onSelect: (String) -> Unit,
    onSelectAuto: () -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onManageSources: () -> Unit,
    onAddSource: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable(domain) { mutableStateOf("") }
    val queryKey = remember(query) { searchKey(query.trim()) }
    val visible = remember(servers, queryKey) {
        if (queryKey.isEmpty()) servers else servers.filter { it.searchIndex.contains(queryKey) }
    }
    val fastest = remember(servers) {
        servers.filter { it.hasMeasuredPing }.minByOrNull { it.pingMs ?: Long.MAX_VALUE }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            ScreenHeader(
                title = if (domain == ConfigDomain.Home) "Select Server" else "ExploreVPN",
                subtitle = when {
                    servers.isEmpty() -> "No servers yet"
                    else -> "${servers.size} available · ${servers.count { it.hasMeasuredPing }} tested"
                },
                onBack = onBack,
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeaderIconButton(
                            icon = AppIcon.Refresh,
                            label = "Refresh sources",
                            enabled = !refreshing,
                            onClick = onRefresh,
                        )
                        HeaderIconButton(
                            icon = AppIcon.Bolt,
                            label = "Test latency",
                            enabled = !pingRunning && servers.isNotEmpty(),
                            onClick = onPing,
                        )
                        HeaderIconButton(
                            icon = AppIcon.Lock,
                            label = "Manage sources",
                            onClick = onManageSources,
                        )
                    }
                },
            )

            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search country, city, or server",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (queryKey.isEmpty() && domain == ConfigDomain.Home) {
                    item(key = "auto-select") {
                        SmartAutoSelectRow(
                            selected = autoSelect,
                            fastestLabel = fastest?.displayName,
                            onClick = onSelectAuto,
                        )
                    }
                }

                if (queryKey.isEmpty()) {
                    item(key = "domain-heading") {
                        SectionLabel(
                            text = if (domain == ConfigDomain.Home) "Home servers" else "Explore servers",
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                } else {
                    item(key = "search-heading") {
                        Text(
                            text = "${visible.size} result${if (visible.size == 1) "" else "s"}",
                            modifier = Modifier.padding(bottom = 2.dp),
                            color = Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                when {
                    loading && servers.isEmpty() -> item(key = "loading") {
                        EmptyState(
                            icon = AppIcon.Refresh,
                            title = "Loading servers",
                            body = "Reading the configurations owned by this section.",
                        )
                    }

                    servers.isEmpty() -> item(key = "empty") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                icon = AppIcon.Lock,
                                title = if (domain == ConfigDomain.Home) {
                                    "No Home server yet"
                                } else {
                                    "ExploreVPN is empty"
                                },
                                body = if (domain == ConfigDomain.Home) {
                                    "Add a subscription source to load Home servers. Nothing is shared with ExploreVPN or Manual Import."
                                } else {
                                    "Add an independent source for ExploreVPN. Its servers are stored separately from Home."
                                },
                            )
                            PrimaryButton(
                                text = "Add source",
                                onClick = onAddSource,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }

                    visible.isEmpty() -> item(key = "no-results") {
                        EmptyState(
                            icon = AppIcon.Search,
                            title = "No servers found",
                            body = "Try another country, city, or server name.",
                        )
                    }

                    else -> items(items = visible, key = { it.guid }) { server ->
                        ServerListRow(
                            server = server,
                            selected = server.guid == selectedGuid,
                            showFlag = domain == ConfigDomain.Home,
                            onClick = { onSelect(server.guid) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: AppIcon,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        DrawIcon(icon, if (enabled) LightBlue else Muted.copy(alpha = 0.4f), Modifier.size(17.dp))
    }
}

@Composable
fun SmartAutoSelectRow(
    selected: Boolean,
    fastestLabel: String?,
    onClick: () -> Unit,
) {
    val iconBackground = remember {
        Brush.linearGradient(listOf(Color(0xFF3E9CFF), Color(0xFF236BDC), Color(0xFF164A9F)))
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = if (selected) {
                    "Auto select is active. The fastest measured Home server will be used."
                } else {
                    "Choose the Home server with the lowest measured latency"
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF173152),
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(47.dp)
                    .background(iconBackground, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                SmartSymbol(Modifier.size(28.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Auto Select",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Automatically choose the best server",
                    color = Color(0xFFD0E2FA),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = fastestLabel?.let { "Fastest measured: $it" }
                        ?: "Uses real latency tests, never cached guesses",
                    color = LightBlue,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(ElectricBlue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    DrawIcon(AppIcon.Check, Color.White, Modifier.size(13.dp))
                }
            } else {
                DrawIcon(AppIcon.Chevron, LightBlue, Modifier.size(17.dp))
            }
        }
    }
}

/**
 * One server row. Only safe fields are rendered; there is deliberately no secondary action
 * that could reveal the underlying configuration.
 */
@Composable
fun ServerListRow(
    server: SafeServer,
    selected: Boolean,
    showFlag: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = when {
                    selected -> "Selected server"
                    else -> "Server"
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF1A3152) else CardColor,
        border = BorderStroke(
            1.dp,
            if (selected) ElectricBlue.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.06f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showFlag) {
                CountryFlagChip(
                    countryCode = server.countryCode,
                    modifier = Modifier.size(43.dp),
                    size = 43.dp,
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(43.dp)
                        .background(ElectricBlue.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    DrawIcon(AppIcon.Globe, LightBlue, Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
            }

            // Compact reference-style row: flag, friendly name and the real ping only.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (server.location.isNotBlank()) {
                    Text(
                        text = server.location,
                        color = Color(0xFFD1DEEF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(6.dp))
            Text(
                text = pingLabel(server.pingMs),
                color = pingColor(server.pingMs),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(ElectricBlue, CircleShape)
                )
            }
        }
    }
}

/**
 * Source management for one domain. URLs are write-only: they can be entered once and are
 * never shown again, not even masked in a tooltip, log, share sheet or accessibility label.
 */
@Composable
fun SourceManagerScreen(
    domain: ConfigDomain,
    sources: List<SourceUi>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onAddSource: () -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    var removeTarget by remember { mutableStateOf<SourceUi?>(null) }

    removeTarget?.let { target ->
        ConfirmDialog(
            title = "Remove source",
            message = "Removing \"${target.name}\" also removes the ${target.serverCount} configuration(s) it imported into this section. Other sources are not affected.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { removeTarget = null },
            onConfirm = {
                removeTarget = null
                onRemoveSource(target.id)
            },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            ScreenHeader(
                title = "Sources",
                subtitle = if (domain == ConfigDomain.Home) {
                    "Home VPN subscriptions"
                } else {
                    "ExploreVPN subscriptions"
                },
                onBack = onBack,
                action = {
                    HeaderIconButton(
                        icon = AppIcon.Refresh,
                        label = "Refresh all sources",
                        enabled = !refreshing && sources.isNotEmpty(),
                        onClick = onRefreshAll,
                    )
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Each source belongs to this section only. Subscription links are stored write-only: they are never displayed again, logged, or copied to the clipboard.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )

                if (sources.isEmpty()) {
                    EmptyState(
                        icon = AppIcon.Lock,
                        title = "No source configured",
                        body = "Add the subscription URLs you own. HardVPN never contacts anything you did not configure.",
                    )
                } else {
                    sources.forEach { source ->
                        SourceRow(
                            source = source,
                            refreshing = refreshing,
                            onRefresh = { onRefreshSource(source.id) },
                            onRemove = { removeTarget = source },
                        )
                    }
                }

                PrimaryButton(
                    text = "Add source",
                    onClick = onAddSource,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !refreshing,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceUi,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
) {
    HardVpnCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${source.serverCount} configuration(s) · " + lastUpdatedLabel(source.lastUpdatedAt),
                        color = Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (source.failedLastRefresh) {
                        Text(
                            text = "Last refresh failed · previous servers kept",
                            color = Color(0xFFFFB08A),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
                HeaderIconButton(
                    icon = AppIcon.Refresh,
                    label = "Refresh ${source.name}",
                    enabled = !refreshing,
                    onClick = onRefresh,
                )
                HeaderIconButton(
                    icon = AppIcon.Trash,
                    label = "Remove ${source.name}",
                    onClick = onRemove,
                )
            }
        }
    }
}

private fun lastUpdatedLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "never updated"
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> "updated just now"
        minutes < 60L -> "updated ${minutes}m ago"
        minutes < 1440L -> "updated ${minutes / 60L}h ago"
        else -> String.format(Locale.US, "updated %dd ago", minutes / 1440L)
    }
}

@Composable
fun AddSourceDialog(
    domain: ConfigDomain,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = {
            Text(
                text = if (domain == ConfigDomain.Home) "Add Home source" else "Add ExploreVPN source",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "The link is stored write-only and is never shown again.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                HardVpnTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Display name",
                    contentDescription = "Source name",
                )
                HardVpnTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://… subscription URL",
                    keyboardType = KeyboardType.Uri,
                    contentDescription = "Subscription URL",
                )
                Text(
                    text = "This source's servers stay inside this section. Nothing is copied into ExploreVPN or Manual Import.",
                    color = Muted.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) {
                Text(text = "Add & import", color = LightBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Muted)
            }
        },
    )
}
