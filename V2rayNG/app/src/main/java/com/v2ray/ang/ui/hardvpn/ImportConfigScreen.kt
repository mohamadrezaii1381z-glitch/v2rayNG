package com.v2ray.ang.ui.hardvpn

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.enums.EConfigType

/**
 * Manual Import: the only HardVPN area that can show and edit technical details, and only for
 * configurations the user imported by hand.
 *
 * Rows here are still safe projections. The technical editor is the existing v2rayNG profile
 * editor, opened with the manual bucket as its subscription, so nothing from Home or ExploreVPN
 * can be edited or exposed through this screen.
 */
@Composable
fun ImportConfigScreen(
    state: HardVpnUiState,
    onSelect: (String) -> Unit,
    onConnectToggle: () -> Unit,
    onImportText: (String?) -> Unit,
    onImportClipboard: () -> Unit,
    onImportQr: () -> Unit,
    onImportFile: () -> Unit,
    onManualEntry: (EConfigType) -> Unit,
    onEdit: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onPing: () -> Unit,
    onDestination: (HardVpnDestination) -> Unit,
) {
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var showProtocolPicker by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SafeServer?>(null) }
    var deleteTarget by remember { mutableStateOf<SafeServer?>(null) }

    if (showPaste) {
        PasteImportDialog(
            importing = state.importing,
            onDismiss = { showPaste = false },
            onConfirm = { text ->
                showPaste = false
                onImportText(text)
            },
        )
    }
    if (showProtocolPicker) {
        ProtocolPickerDialog(
            onDismiss = { showProtocolPicker = false },
            onPick = { type ->
                showProtocolPicker = false
                onManualEntry(type)
            },
        )
    }
    renameTarget?.let { target ->
        RenameManualDialog(
            server = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                onRename(target.guid, newName)
            },
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete configuration",
            message = "\"${target.displayName}\" will be permanently removed from Manual Import. Home and ExploreVPN are not affected.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                onDelete(target.guid)
            },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            ScreenHeader(
                title = "Import Config",
                subtitle = "Your own configurations · stored separately",
                action = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = !state.pingRunning && state.manualServers.isNotEmpty(),
                                role = Role.Button,
                                onClickLabel = "Test latency",
                                onClick = onPing,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        DrawIcon(
                            AppIcon.Bolt,
                            if (!state.pingRunning && state.manualServers.isNotEmpty()) {
                                LightBlue
                            } else {
                                Muted.copy(alpha = 0.4f)
                            },
                            Modifier.size(17.dp),
                        )
                    }
                },
            )

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
                item(key = "manual-status") {
                    ManualStatusStrip(state = state, onConnectToggle = onConnectToggle)
                }

                item(key = "manual-import-actions") {
                    ImportActionsCard(
                        importing = state.importing,
                        onPaste = { showPaste = true },
                        onClipboard = onImportClipboard,
                        onQr = onImportQr,
                        onFile = onImportFile,
                        onManualEntry = { showProtocolPicker = true },
                    )
                }

                item(key = "manual-heading") {
                    SectionLabel(
                        text = if (state.manualServers.isEmpty()) {
                            "No manual configuration"
                        } else {
                            "${state.manualServers.size} configuration(s)"
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                if (state.manualServers.isEmpty()) {
                    item(key = "manual-empty") {
                        EmptyState(
                            icon = AppIcon.Download,
                            title = "Nothing imported yet",
                            body = "Paste a configuration link, scan a QR code, open a file, or enter one by hand. Subscription links belong to Home VPN or ExploreVPN sources.",
                        )
                    }
                } else {
                    items(items = state.manualServers, key = { it.guid }) { server ->
                        ManualServerRow(
                            server = server,
                            selected = server.guid == state.selectedManual,
                            onSelect = { onSelect(server.guid) },
                            onEdit = { onEdit(server.guid) },
                            onRename = { renameTarget = server },
                            onDelete = { deleteTarget = server },
                        )
                    }
                }

                item(key = "manual-note") {
                    Text(
                        text = "Manual Import is the only place where the technical details of your own imports can be viewed and edited. Home and ExploreVPN configurations stay protected.",
                        color = Muted.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            HardVpnBottomNavigation(
                selected = HardVpnDestination.ImportConfig,
                compact = false,
                onSelect = onDestination,
            )
        }
    }
}

@Composable
private fun ManualStatusStrip(state: HardVpnUiState, onConnectToggle: () -> Unit) {
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
                DrawIcon(AppIcon.Download, Color.White, Modifier.size(19.dp))
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
                    text = if (state.activeDomain == ConfigDomain.Manual) {
                        "Active source: Manual import"
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
                    state.isConnected && state.activeDomain == ConfigDomain.Manual -> "Disconnect"
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
private fun ImportActionsCard(
    importing: Boolean,
    onPaste: () -> Unit,
    onClipboard: () -> Unit,
    onQr: () -> Unit,
    onFile: () -> Unit,
    onManualEntry: () -> Unit,
) {
    HardVpnCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Text(
                text = "Add a configuration",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Supported links, QR codes, files and manual entry. Everything stays in this section.",
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = if (importing) "Importing…" else "Paste",
                    onClick = onPaste,
                    enabled = !importing,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Clipboard",
                    onClick = onClipboard,
                    enabled = !importing,
                    outlined = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "QR code",
                    onClick = onQr,
                    enabled = !importing,
                    outlined = true,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "File",
                    onClick = onFile,
                    enabled = !importing,
                    outlined = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = "Manual entry",
                onClick = onManualEntry,
                enabled = !importing,
                outlined = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ManualServerRow(
    server: SafeServer,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    HardVpnCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) {
            BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f))
        } else {
            CardBorder
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountryFlagChip(
                    countryCode = server.countryCode,
                    modifier = Modifier.size(40.dp),
                    size = 40.dp,
                )
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = server.location.ifBlank { "Manual import" },
                        color = Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (selected) "Selected" else "Tap to select",
                        color = if (selected) LightBlue else Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = pingLabel(server.pingMs),
                        color = pingColor(server.pingMs),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = if (server.hasMeasuredPing) "measured" else "untested",
                        color = Muted,
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    SignalBars(
                        pingMs = server.pingMs,
                        modifier = Modifier.size(width = 17.dp, height = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Select",
                    onClick = onSelect,
                    outlined = !selected,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Edit",
                    onClick = onEdit,
                    outlined = true,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Rename",
                    onClick = onRename,
                    outlined = true,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Delete",
                    onClick = onDelete,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PasteImportDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = { Text(text = "Paste configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste a supported configuration link or text. Subscription links are rejected here on purpose.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                HardVpnTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "vmess://… / vless://… / trojan://…",
                    singleLine = false,
                    imeAction = ImeAction.Done,
                    minHeight = 96.dp,
                    contentDescription = "Configuration text",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && !importing,
            ) {
                Text(
                    text = if (importing) "Importing…" else "Import",
                    color = LightBlue,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Muted)
            }
        },
    )
}

@Composable
private fun RenameManualDialog(
    server: SafeServer,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(server.guid) { mutableStateOf(server.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = { Text(text = "Rename configuration", fontWeight = FontWeight.Bold) },
        text = {
            HardVpnTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Display name",
                contentDescription = "Configuration name",
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(text = "Save", color = LightBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Muted)
            }
        },
    )
}

private val MANUAL_ENTRY_TYPES = listOf(
    EConfigType.VMESS to "VMess",
    EConfigType.VLESS to "VLESS",
    EConfigType.SHADOWSOCKS to "Shadowsocks",
    EConfigType.SOCKS to "SOCKS",
    EConfigType.TROJAN to "Trojan",
    EConfigType.WIREGUARD to "WireGuard",
    EConfigType.HYSTERIA2 to "Hysteria2",
    EConfigType.HTTP to "HTTP",
    EConfigType.MASQUE to "MASQUE",
    EConfigType.CUSTOM to "Custom JSON",
    EConfigType.POLICYGROUP to "Policy group",
    EConfigType.PROXYCHAIN to "Proxy chain",
)

@Composable
private fun ProtocolPickerDialog(
    onDismiss: () -> Unit,
    onPick: (EConfigType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = { Text(text = "Manual entry", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Choose a protocol. The editor opens with the manual bucket, so the new configuration stays in Manual Import.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                MANUAL_ENTRY_TYPES.forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = { onPick(type) })
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        DrawIcon(AppIcon.Chevron, Muted, Modifier.size(14.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Muted)
            }
        },
    )
}
