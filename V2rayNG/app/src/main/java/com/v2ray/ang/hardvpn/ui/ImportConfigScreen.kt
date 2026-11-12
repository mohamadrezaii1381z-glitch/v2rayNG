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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.hardvpn.HardVpnAction
import com.v2ray.ang.hardvpn.domain.ConnectionPhase
import com.v2ray.ang.hardvpn.domain.HardVpnUiState
import com.v2ray.ang.hardvpn.domain.ManualServerUiModel

/**
 * Manual Import.
 *
 * Configurations here are imported by the user and stored in their own bucket. Technical details
 * are allowed only on this destination because the user owns these configurations.
 */
@Composable
fun ImportConfigScreen(
    state: HardVpnUiState,
    onAction: (HardVpnAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manual = state.manual
    val connection = state.connection
    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var showPasteDialog by rememberSaveable { mutableStateOf(false) }
    var showTypeSheet by rememberSaveable { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ManualServerUiModel?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ManualServerUiModel?>(null) }

    val filtered = remember(manual.servers, manual.query) {
        val query = manual.query.trim()
        if (query.isEmpty()) manual.servers
        else manual.servers.filter { it.name.contains(query, ignoreCase = true) }
    }
    val selected = manual.servers.firstOrNull { it.isSelected }
    val connectSubtitle = selected?.name
        ?: stringResource(R.string.hardvpn_manual_select_hint)

    if (showAddMenu) {
        AddConfigSheet(
            onDismiss = { showAddMenu = false },
            onClipboard = {
                showAddMenu = false
                onAction(HardVpnAction.RequestClipboardImport)
            },
            onQr = {
                showAddMenu = false
                onAction(HardVpnAction.RequestQrImport)
            },
            onFile = {
                showAddMenu = false
                onAction(HardVpnAction.RequestFileImport)
            },
            onPaste = {
                showAddMenu = false
                showPasteDialog = true
            },
            onManualEntry = {
                showAddMenu = false
                showTypeSheet = true
            },
            onAdvancedTools = {
                showAddMenu = false
                onAction(HardVpnAction.OpenLegacyTools)
            },
        )
    }

    if (showTypeSheet) {
        ConfigTypeSheet(
            onDismiss = { showTypeSheet = false },
            onSelect = { type ->
                showTypeSheet = false
                onAction(HardVpnAction.AddManual(type.value))
            },
        )
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = HardVpnColors.SurfaceHigh,
            titleContentColor = HardVpnColors.TextPrimary,
            textContentColor = HardVpnColors.TextSecondary,
            title = { Text(stringResource(R.string.hardvpn_import_paste_title)) },
            text = {
                HardVpnTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    placeholder = stringResource(R.string.hardvpn_import_paste_hint),
                    singleLine = false,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasteDialog = false
                        val text = pasteText
                        pasteText = ""
                        onAction(HardVpnAction.ImportText(text))
                    },
                    enabled = pasteText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.hardvpn_import_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text(stringResource(R.string.hardvpn_cancel))
                }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = HardVpnColors.SurfaceHigh,
            titleContentColor = HardVpnColors.TextPrimary,
            textContentColor = HardVpnColors.TextSecondary,
            title = { Text(stringResource(R.string.hardvpn_manual_rename_title)) },
            text = {
                HardVpnTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = stringResource(R.string.hardvpn_manual_name),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(HardVpnAction.RenameManual(target.guid, renameText))
                        renameTarget = null
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.hardvpn_manual_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.hardvpn_cancel))
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = HardVpnColors.SurfaceHigh,
            titleContentColor = HardVpnColors.TextPrimary,
            textContentColor = HardVpnColors.TextSecondary,
            title = { Text(stringResource(R.string.hardvpn_manual_delete_title)) },
            text = { Text(stringResource(R.string.hardvpn_manual_delete_body, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onAction(HardVpnAction.DeleteManual(target.guid))
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.hardvpn_delete), color = HardVpnColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.hardvpn_cancel))
                }
            },
        )
    }

    HardVpnSecondaryBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.hardvpn_tab_import),
                subtitle = stringResource(
                    R.string.hardvpn_import_count,
                    manual.servers.size,
                ),
                onBack = null,
                actions = {
                    IconAction(
                        icon = Icons.Filled.Add,
                        description = stringResource(R.string.hardvpn_import_add),
                        onClick = { showAddMenu = true },
                    )
                    IconAction(
                        icon = Icons.Filled.Settings,
                        description = stringResource(R.string.hardvpn_import_advanced),
                        onClick = { onAction(HardVpnAction.OpenLegacyTools) },
                    )
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.hardvpn_import_subtitle),
                    color = HardVpnColors.TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                ConnectBar(
                    phase = connection.phase,
                    subtitle = connectSubtitle,
                    onToggle = { onAction(HardVpnAction.ToggleConnection) },
                )
                Spacer(Modifier.height(10.dp))
                HardVpnSearchField(
                    value = manual.query,
                    onValueChange = { onAction(HardVpnAction.UpdateManualQuery(it)) },
                )
                if (manual.isTesting) {
                    Spacer(Modifier.height(8.dp))
                    BusyLine(text = stringResource(R.string.hardvpn_testing))
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                manual.isLoading -> LoadingBlock()
                manual.servers.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        icon = Icons.Filled.Add,
                        title = stringResource(R.string.hardvpn_import_empty_title),
                        body = stringResource(R.string.hardvpn_import_empty_body),
                        actionLabel = stringResource(R.string.hardvpn_import_add),
                        onAction = { showAddMenu = true },
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = filtered, key = { it.guid }) { server ->
                        ManualServerRow(
                            server = server,
                            selected = server.isSelected,
                            active = connection.phase == ConnectionPhase.CONNECTED &&
                                server.guid == connection.activeGuid,
                            onSelect = { onAction(HardVpnAction.SelectManual(server.guid)) },
                            onEdit = {
                                onAction(
                                    HardVpnAction.EditManual(server.guid, server.configTypeValue)
                                )
                            },
                            onRename = {
                                renameTarget = server
                                renameText = server.name
                            },
                            onDelete = { deleteTarget = server },
                            onTest = { onAction(HardVpnAction.TestManual) },
                            onConnect = { onAction(HardVpnAction.ConnectManual(server.guid)) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.hardvpn_manual_technical_note),
                            color = HardVpnColors.TextMuted,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        AdvancedToolsRow(
                            onClick = { onAction(HardVpnAction.OpenLegacyTools) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualServerRow(
    server: ManualServerUiModel,
    selected: Boolean,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onConnect: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color(0xFF14294F) else Color(0xFF0B1730))
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF13244A)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = HardVpnColors.AccentBright,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = server.name,
                color = HardVpnColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = server.summary?.takeIf { it.isNotBlank() }
                    ?: configTypeLabel(server.configTypeValue),
                color = HardVpnColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PingChip(server.pingMillis)
        if (active || selected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (active) HardVpnColors.Success else HardVpnColors.AccentBright,
                modifier = Modifier.size(16.dp),
            )
        }
        Box {
            IconAction(
                icon = Icons.Filled.MoreVert,
                description = stringResource(R.string.hardvpn_more_actions),
                onClick = { menuOpen = true },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = HardVpnColors.SurfaceHigh,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hardvpn_action_connect)) },
                    onClick = { menuOpen = false; onConnect() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hardvpn_manual_edit)) },
                    onClick = { menuOpen = false; onEdit() },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hardvpn_manual_rename)) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hardvpn_test_latency)) },
                    onClick = { menuOpen = false; onTest() },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hardvpn_delete)) },
                    onClick = { menuOpen = false; onDelete() },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = HardVpnColors.Danger,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun configTypeLabel(value: Int): String {
    val type = EConfigType.fromInt(value)
    return when (type) {
        EConfigType.VMESS -> "VMess"
        EConfigType.VLESS -> "VLESS"
        EConfigType.SHADOWSOCKS -> "Shadowsocks"
        EConfigType.SOCKS -> "SOCKS"
        EConfigType.HTTP -> "HTTP"
        EConfigType.TROJAN -> "Trojan"
        EConfigType.WIREGUARD -> "WireGuard"
        EConfigType.HYSTERIA2, EConfigType.HYSTERIA -> "Hysteria2"
        EConfigType.MASQUE -> "MASQUE"
        EConfigType.CUSTOM, EConfigType.CUSTOM_OUTBOUND -> "Custom"
        EConfigType.POLICYGROUP -> "Policy group"
        EConfigType.PROXYCHAIN -> "Proxy chain"
        null -> stringResource(R.string.hardvpn_manual_type_unknown)
    }
}

@Composable
private fun AdvancedToolsRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101F3C))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = HardVpnColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hardvpn_import_advanced),
                color = HardVpnColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.hardvpn_import_advanced_body),
                color = HardVpnColors.TextMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConfigSheet(
    onDismiss: () -> Unit,
    onClipboard: () -> Unit,
    onQr: () -> Unit,
    onFile: () -> Unit,
    onPaste: () -> Unit,
    onManualEntry: () -> Unit,
    onAdvancedTools: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.hardvpn_import_add),
                color = HardVpnColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            SheetOption(stringResource(R.string.hardvpn_import_clipboard), onClipboard)
            SheetOption(stringResource(R.string.hardvpn_import_qr), onQr)
            SheetOption(stringResource(R.string.hardvpn_import_file), onFile)
            SheetOption(stringResource(R.string.hardvpn_import_paste), onPaste)
            SheetOption(stringResource(R.string.hardvpn_import_manual_entry), onManualEntry)
            SheetOption(stringResource(R.string.hardvpn_import_advanced), onAdvancedTools)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SheetOption(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = HardVpnColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigTypeSheet(
    onDismiss: () -> Unit,
    onSelect: (EConfigType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = listOf(
        R.string.menu_item_import_config_manually_vmess to EConfigType.VMESS,
        R.string.menu_item_import_config_manually_vless to EConfigType.VLESS,
        R.string.menu_item_import_config_manually_ss to EConfigType.SHADOWSOCKS,
        R.string.menu_item_import_config_manually_socks to EConfigType.SOCKS,
        R.string.menu_item_import_config_manually_http to EConfigType.HTTP,
        R.string.menu_item_import_config_manually_trojan to EConfigType.TROJAN,
        R.string.menu_item_import_config_manually_wireguard to EConfigType.WIREGUARD,
        R.string.menu_item_import_config_manually_hysteria2 to EConfigType.HYSTERIA2,
        R.string.menu_item_import_config_manually_masque to EConfigType.MASQUE,
    )
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.hardvpn_manual_type_selection),
                color = HardVpnColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            options.forEach { (labelRes, type) ->
                SheetOption(stringResource(labelRes)) { onSelect(type) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
