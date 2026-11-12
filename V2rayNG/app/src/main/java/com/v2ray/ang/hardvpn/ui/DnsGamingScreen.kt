package com.v2ray.ang.hardvpn.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.v2ray.ang.hardvpn.domain.DnsPreset
import com.v2ray.ang.hardvpn.domain.DnsProfile
import com.v2ray.ang.hardvpn.domain.DnsUiState

/**
 * DNS Gaming: applies a resolver to the real VPN DNS setting the core reads.
 *
 * Electro DNS stays explicitly unavailable because no verified address exists in this project;
 * no address is invented. Applied state is read back from the engine settings.
 */
@Composable
fun DnsGamingScreen(
    state: DnsUiState,
    onAction: (HardVpnAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var primary by remember(state.appliedPrimary) {
        mutableStateOf(state.appliedPrimary.orEmpty())
    }
    var secondary by remember(state.appliedSecondary) {
        mutableStateOf(state.appliedSecondary.orEmpty())
    }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var profilePrimary by remember { mutableStateOf("") }
    var profileSecondary by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DnsProfile?>(null) }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = HardVpnColors.SurfaceHigh,
            titleContentColor = HardVpnColors.TextPrimary,
            textContentColor = HardVpnColors.TextSecondary,
            title = { Text(stringResource(R.string.hardvpn_dns_profile_add)) },
            text = {
                Column {
                    HardVpnTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        placeholder = stringResource(R.string.hardvpn_dns_profile_name),
                    )
                    Spacer(Modifier.height(8.dp))
                    HardVpnTextField(
                        value = profilePrimary,
                        onValueChange = { profilePrimary = it },
                        placeholder = stringResource(R.string.hardvpn_dns_primary),
                    )
                    Spacer(Modifier.height(8.dp))
                    HardVpnTextField(
                        value = profileSecondary,
                        onValueChange = { profileSecondary = it },
                        placeholder = stringResource(R.string.hardvpn_dns_secondary),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(
                            HardVpnAction.DnsSaveProfile(
                                id = null,
                                name = profileName,
                                primary = profilePrimary,
                                secondary = profileSecondary,
                            )
                        )
                        showSaveDialog = false
                    },
                    enabled = profileName.isNotBlank() && profilePrimary.isNotBlank(),
                ) {
                    Text(stringResource(R.string.hardvpn_dns_profile_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
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
            title = { Text(stringResource(R.string.hardvpn_dns_profile_delete_title)) },
            text = { Text(stringResource(R.string.hardvpn_dns_profile_delete_body, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onAction(HardVpnAction.DnsDeleteProfile(target.id))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            ScreenHeader(
                title = stringResource(R.string.hardvpn_tab_dns),
                subtitle = stringResource(R.string.hardvpn_dns_subtitle),
                onBack = null,
                actions = {
                    IconAction(
                        icon = Icons.Filled.Refresh,
                        description = stringResource(R.string.hardvpn_refresh),
                        onClick = { onAction(HardVpnAction.RefreshDns) },
                    )
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppliedDnsCard(state)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.hardvpn_dns_presets_title),
                    color = HardVpnColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                DnsPresetCard(
                    title = stringResource(R.string.hardvpn_dns_electro),
                    subtitle = stringResource(R.string.hardvpn_dns_electro_body),
                    badge = stringResource(R.string.hardvpn_dns_electro_badge),
                    badgeColor = HardVpnColors.Gold,
                    enabled = false,
                    applied = false,
                    onClick = { onAction(HardVpnAction.DnsApplyPreset(DnsPreset.ELECTRO)) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.hardvpn_dns_unavailable_hint),
                    color = HardVpnColors.TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                DnsPresetCard(
                    title = stringResource(R.string.hardvpn_dns_cloudflare),
                    subtitle = stringResource(R.string.hardvpn_dns_cloudflare_body),
                    badge = if (state.preset == DnsPreset.CLOUDFLARE &&
                        state.appliedPrimary == "1.1.1.1"
                    ) {
                        stringResource(R.string.hardvpn_dns_applied_badge)
                    } else {
                        null
                    },
                    badgeColor = HardVpnColors.Success,
                    enabled = true,
                    applied = state.appliedPrimary == "1.1.1.1" &&
                        state.appliedSecondary == "1.0.0.1",
                    onClick = { onAction(HardVpnAction.DnsApplyPreset(DnsPreset.CLOUDFLARE)) },
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.hardvpn_dns_custom),
                    color = HardVpnColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.hardvpn_dns_custom_body),
                    color = HardVpnColors.TextMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                HardVpnTextField(
                    value = primary,
                    onValueChange = { primary = it },
                    placeholder = stringResource(R.string.hardvpn_dns_primary),
                )
                Spacer(Modifier.height(8.dp))
                HardVpnTextField(
                    value = secondary,
                    onValueChange = { secondary = it },
                    placeholder = stringResource(R.string.hardvpn_dns_secondary),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    label = stringResource(R.string.hardvpn_dns_apply),
                    enabled = primary.isNotBlank(),
                    onClick = { onAction(HardVpnAction.DnsApplyCustom(primary, secondary)) },
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.hardvpn_dns_profiles),
                        color = HardVpnColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.hardvpn_dns_profile_add),
                        color = HardVpnColors.AccentBright,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) {
                                profileName = ""
                                profilePrimary = primary
                                profileSecondary = secondary
                                showSaveDialog = true
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state.profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.hardvpn_dns_profiles_empty),
                        color = HardVpnColors.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    state.profiles.forEach { profile ->
                        DnsProfileRow(
                            profile = profile,
                            applied = profile.primary == state.appliedPrimary &&
                                (profile.secondary ?: "") == (state.appliedSecondary ?: ""),
                            onApply = { onAction(HardVpnAction.DnsApplyProfile(profile.id)) },
                            onDelete = { deleteTarget = profile },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppliedDnsCard(state: DnsUiState) {
    HardVpnCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF122B52)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = HardVpnColors.AccentBright,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hardvpn_dns_applied),
                    color = HardVpnColors.TextMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        state.appliedPrimary.isNullOrBlank() ->
                            stringResource(R.string.hardvpn_dns_none)

                        state.appliedSecondary.isNullOrBlank() -> state.appliedPrimary.orEmpty()
                        else -> "${state.appliedPrimary} · ${state.appliedSecondary}"
                    },
                    color = HardVpnColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.requiresReconnect) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.hardvpn_dns_requires_reconnect),
                color = HardVpnColors.Gold,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun DnsPresetCard(
    title: String,
    subtitle: String,
    badge: String?,
    badgeColor: Color,
    enabled: Boolean,
    applied: Boolean,
    onClick: () -> Unit,
) {
    HardVpnCard(
        onClickLabel = title,
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF12244A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = if (enabled) HardVpnColors.AccentBright else HardVpnColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) HardVpnColors.TextPrimary else HardVpnColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = HardVpnColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            if (badge != null) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else if (applied) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = HardVpnColors.Success,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DnsProfileRow(
    profile: DnsProfile,
    applied: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101F3C))
            .border(
                1.dp,
                if (applied) HardVpnColors.Success.copy(alpha = 0.5f)
                else Color(0x1F6E8CBF),
                RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.Button, onClick = onApply)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = HardVpnColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(profile.primary, profile.secondary).joinToString(" · "),
                color = HardVpnColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (applied) {
            Text(
                text = stringResource(R.string.hardvpn_dns_applied_badge),
                color = HardVpnColors.Success,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        IconAction(
            icon = Icons.Filled.Delete,
            description = stringResource(R.string.hardvpn_dns_profile_delete_a11y, profile.name),
            onClick = onDelete,
        )
    }
}
