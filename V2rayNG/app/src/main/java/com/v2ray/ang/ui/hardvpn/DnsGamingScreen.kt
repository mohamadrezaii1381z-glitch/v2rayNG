package com.v2ray.ang.ui.hardvpn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

/**
 * DNS Gaming: presets, custom resolvers and saved profiles.
 *
 * Every apply goes through the existing engine DNS settings ([HardVpnRepository.applyDns]).
 * "Applied" is only shown after that write succeeded, and when a tunnel is running the screen
 * says the change needs a reconnect instead of pretending it is already live.
 */
@Composable
fun DnsGamingScreen(
    state: HardVpnUiState,
    onApplyPreset: (name: String, primary: String, secondary: String) -> Unit,
    onApplyProfile: (DnsProfile) -> Unit,
    onSaveProfile: (DnsProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDeactivateDns: () -> Unit,
    onDestination: (HardVpnDestination) -> Unit,
) {
    var editingProfile by remember { mutableStateOf<DnsProfile?>(null) }
    var creatingProfile by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DnsProfile?>(null) }
    var customName by rememberSaveable { mutableStateOf("") }
    var customPrimary by rememberSaveable { mutableStateOf("") }
    var customSecondary by rememberSaveable { mutableStateOf("") }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete DNS profile",
            message = "\"${target.name}\" will be removed from this device. The currently applied DNS settings are not changed.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                onDeleteProfile(target.id)
            },
        )
    }

    if (creatingProfile || editingProfile != null) {
        DnsProfileDialog(
            initial = editingProfile,
            onDismiss = {
                creatingProfile = false
                editingProfile = null
            },
            onConfirm = { profile ->
                creatingProfile = false
                editingProfile = null
                onSaveProfile(profile)
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
                title = "DNS Gaming",
                subtitle = "Resolvers for the next connection · saved on this device",
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(2.dp))

                CurrentDnsCard(dns = state.dns, onDeactivate = onDeactivateDns)

                // No DNS provider is pre-inserted or silently enabled: the user enters
                // resolver addresses manually below.

                HardVpnCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF39E5AD), Color(0xFF0CA475))
                                        ),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                DrawIcon(AppIcon.Edit, Color.White, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Custom DNS",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "Your own resolvers",
                                    color = Muted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        HardVpnTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            placeholder = "Profile name (for saving)",
                            contentDescription = "Custom DNS profile name",
                        )
                        HardVpnTextField(
                            value = customPrimary,
                            onValueChange = { customPrimary = it },
                            placeholder = "Primary DNS (e.g. 8.8.8.8)",
                            keyboardType = KeyboardType.Uri,
                            contentDescription = "Custom primary DNS",
                        )
                        HardVpnTextField(
                            value = customSecondary,
                            onValueChange = { customSecondary = it },
                            placeholder = "Secondary DNS (optional)",
                            keyboardType = KeyboardType.Uri,
                            contentDescription = "Custom secondary DNS",
                        )
                        PrimaryButton(
                            text = "Activate DNS",
                            onClick = {
                                onApplyPreset(
                                    "Custom DNS",
                                    customPrimary.trim(),
                                    customSecondary.trim(),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = customPrimary.isNotBlank(),
                        )
                        PrimaryButton(
                            text = "Save profile",
                            onClick = {
                                onSaveProfile(
                                    DnsProfile(
                                        id = UUID.randomUUID().toString(),
                                        name = customName.trim(),
                                        primary = customPrimary.trim(),
                                        secondary = customSecondary.trim(),
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = customName.isNotBlank() && customPrimary.isNotBlank(),
                            outlined = true,
                        )
                        Text(
                            text = "Only plain IPv4 or IPv6 resolver addresses are accepted. The engine rejects anything else and the screen reports that honestly.",
                            color = Muted.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }

                SectionLabel(
                    text = "Saved profiles",
                    modifier = Modifier.padding(top = 6.dp),
                )

                if (state.dns.profiles.isEmpty()) {
                    EmptyState(
                        icon = AppIcon.Lock,
                        title = "No saved DNS profile",
                        body = "Create a named profile for a resolver pair you use often. Profiles stay on this device.",
                    )
                } else {
                    state.dns.profiles.forEach { profile ->
                        SavedDnsProfileRow(
                            profile = profile,
                            active = profile.id == state.dns.activeProfileId,
                            onApply = { onApplyProfile(profile) },
                            onEdit = { editingProfile = profile },
                            onDelete = { deleteTarget = profile },
                        )
                    }
                }

                PrimaryButton(
                    text = "New DNS profile",
                    onClick = { creatingProfile = true },
                    modifier = Modifier.fillMaxWidth(),
                    outlined = true,
                )
                Spacer(Modifier.height(16.dp))
            }

            HardVpnBottomNavigation(
                selected = HardVpnDestination.DnsGaming,
                compact = false,
                onSelect = onDestination,
            )
        }
    }
}

@Composable
private fun CurrentDnsCard(dns: DnsUiState, onDeactivate: () -> Unit) {
    val activeName = dns.activeProfile?.name
    HardVpnCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(HardGreen, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Currently configured",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = dns.remoteDns.ifBlank { "System default" },
                color = LightBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    activeName != null -> "Profile: $activeName"
                    dns.remoteDns.isBlank() -> "No HardVPN profile applied yet"
                    else -> "Preset or custom value"
                },
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (dns.runningTunnelNeedsReconnect) {
                Text(
                    text = "A tunnel is running: the new DNS takes effect after it reconnects.",
                    color = Color(0xFFFFB08A),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            } else {
                Text(
                    text = "Applied values are used when the next connection starts.",
                    color = Muted.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
            if (dns.remoteDns.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Disconnect DNS",
                    onClick = onDeactivate,
                    modifier = Modifier.fillMaxWidth(),
                    outlined = true,
                )
            }
        }
    }
}

@Composable
private fun DnsPresetCard(
    title: String,
    subtitle: String,
    iconBackground: Brush,
    applied: Boolean,
    enabled: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    HardVpnCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (applied) {
            BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f))
        } else {
            CardBorder
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconBackground, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                DrawIcon(AppIcon.Globe, Color.White, Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (applied) "$subtitle · applied" else subtitle,
                    color = if (applied) LightBlue else Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                enabled = enabled,
                outlined = true,
            )
        }
    }
}

@Composable
private fun SavedDnsProfileRow(
    profile: DnsProfile,
    active: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    HardVpnCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (active) {
            BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f))
        } else {
            CardBorder
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profile.summary.ifBlank { "No resolver" },
                        color = Muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Text(
                            text = "Applied",
                            color = LightBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    text = "Apply",
                    onClick = onApply,
                    outlined = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Edit",
                    onClick = onEdit,
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
private fun DnsProfileDialog(
    initial: DnsProfile?,
    onDismiss: () -> Unit,
    onConfirm: (DnsProfile) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var primary by rememberSaveable(initial?.id) { mutableStateOf(initial?.primary.orEmpty()) }
    var secondary by rememberSaveable(initial?.id) { mutableStateOf(initial?.secondary.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = {
            Text(
                text = if (initial == null) "New DNS profile" else "Edit DNS profile",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HardVpnTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Profile name",
                    contentDescription = "DNS profile name",
                )
                HardVpnTextField(
                    value = primary,
                    onValueChange = { primary = it },
                    placeholder = "Primary DNS",
                    keyboardType = KeyboardType.Uri,
                    contentDescription = "Primary DNS",
                )
                HardVpnTextField(
                    value = secondary,
                    onValueChange = { secondary = it },
                    placeholder = "Secondary DNS (optional)",
                    keyboardType = KeyboardType.Uri,
                    contentDescription = "Secondary DNS",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        DnsProfile(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            primary = primary.trim(),
                            secondary = secondary.trim(),
                        )
                    )
                },
                enabled = name.isNotBlank() && primary.isNotBlank(),
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
