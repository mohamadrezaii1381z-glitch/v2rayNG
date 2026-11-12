package com.v2ray.ang.hardvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/**
 * Premium presentation only.
 *
 * No plan is purchasable and no entitlement is granted: payment integration is explicitly deferred
 * until the free application is complete, so the call to action is disabled instead of faked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSheet(onDismiss: () -> Unit) {
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFCE3A1), Color(0xFFD89B2B))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFF3A2703),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.hardvpn_premium_title),
                        color = HardVpnColors.TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.hardvpn_premium_subtitle),
                        color = HardVpnColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            PremiumFeature(stringResource(R.string.hardvpn_premium_feature_servers))
            PremiumFeature(stringResource(R.string.hardvpn_premium_feature_protocols))
            PremiumFeature(stringResource(R.string.hardvpn_premium_feature_support))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumPlan(
                    name = stringResource(R.string.hardvpn_premium_monthly),
                    modifier = Modifier.weight(1f),
                )
                PremiumPlan(
                    name = stringResource(R.string.hardvpn_premium_yearly),
                    modifier = Modifier.weight(1f),
                    highlighted = true,
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF3A2C0B))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.hardvpn_premium_cta),
                    color = HardVpnColors.Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.hardvpn_premium_not_available),
                color = HardVpnColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PremiumFeature(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0x22F6C453)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = HardVpnColors.Gold,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = HardVpnColors.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PremiumPlan(name: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) Color(0xFF1E2B17) else Color(0xFF101F3C))
            .border(
                1.dp,
                if (highlighted) HardVpnColors.Gold.copy(alpha = 0.55f) else Color(0x1F6E8CBF),
                RoundedCornerShape(16.dp),
            )
            .padding(12.dp)
    ) {
        Text(
            text = name,
            color = HardVpnColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.hardvpn_premium_price_tba),
            color = HardVpnColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}
