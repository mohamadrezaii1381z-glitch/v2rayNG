package com.v2ray.ang.ui.hardvpn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Home screen. Layout, spacing, colours and control shapes follow the supplied design;
 * every dynamic value comes from [HardVpnUiState], which is fed by the real engine.
 */
@Composable
fun HomeScreen(
    state: HardVpnUiState,
    isResumed: Boolean,
    onToggleConnection: () -> Unit,
    onPremiumClick: () -> Unit,
    onServerCardClick: () -> Unit,
    onSponsorsClick: () -> Unit,
    onSampleTraffic: () -> Unit,
    onDestination: (HardVpnDestination) -> Unit,
    onRefreshHome: () -> Unit,
) {
    LaunchedEffect(state.phase, isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (state.phase == ConnectionPhase.Connected) {
            onSampleTraffic()
            delay(TRAFFIC_SAMPLE_INTERVAL_MS)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Static Earth background: it never changes with the selected server or country.
            GlobeBackground(modifier = Modifier.fillMaxSize())

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val compact = maxHeight < 680.dp
                val shortScreen = maxHeight < 600.dp
                val horizontalPadding = if (maxWidth < 360.dp) 16.dp else 24.dp
                val cardGap = if (compact) 8.dp else 12.dp
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .heightIn(min = maxHeight)
                ) {
                    HomeHeader(
                        compact = compact,
                        horizontalPadding = horizontalPadding,
                        onPremiumClick = onPremiumClick,
                        onRefresh = onRefreshHome,
                        refreshing = state.refreshing,
                    )

                    ConnectionPanel(
                        phase = state.phase,
                        sessionStartedAt = state.sessionStartedAt,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .heightIn(min = if (shortScreen) 190.dp else 250.dp)
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding),
                        onToggle = onToggleConnection,
                    )

                    if (state.phase == ConnectionPhase.Error) {
                        Text(
                            text = state.statusMessage ?: "The VPN service could not start",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalPadding, vertical = 4.dp),
                            color = Color(0xFFFF9DAF),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                bottom = if (compact) 8.dp else 14.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(cardGap),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatisticCard(
                                title = "Download",
                                icon = AppIcon.Download,
                                accent = ElectricBlue,
                                speedBytesPerSecond = state.downloadSpeed,
                                totalBytes = state.totalDownload,
                                connected = state.isConnected,
                                compact = compact,
                                modifier = Modifier.weight(1f),
                            )
                            StatisticCard(
                                title = "Upload",
                                icon = AppIcon.Upload,
                                accent = UploadAccent,
                                speedBytesPerSecond = state.uploadSpeed,
                                totalBytes = state.totalUpload,
                                connected = state.isConnected,
                                compact = compact,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        SelectedServerCard(
                            serverName = state.activeServerName,
                            countryCode = state.activeServerCountryCode,
                            location = state.activeServerLocation,
                            pingMs = state.activeServerPing,
                            sourceLabel = state.activeSourceLabel,
                            autoSelect = state.autoSelect,
                            compact = compact,
                            onClick = onServerCardClick,
                        )

                        SponsorsCard(onOpen = onSponsorsClick)
                    }

                    HardVpnBottomNavigation(
                        selected = state.destination,
                        compact = shortScreen,
                        onSelect = onDestination,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    compact: Boolean,
    horizontalPadding: Dp,
    onPremiumClick: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = horizontalPadding,
                vertical = if (compact) 10.dp else 18.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(ElectricBlue.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                DrawIcon(AppIcon.Shield, Color(0xFF5CA2FF), Modifier.size(20.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = "HardVPN",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .clickable(
                        enabled = !refreshing,
                        role = Role.Button,
                        onClickLabel = "Refresh Home sources",
                        onClick = onRefresh,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                DrawIcon(
                    icon = AppIcon.Refresh,
                    color = if (refreshing) Muted else LightBlue,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            onClick = onPremiumClick,
            modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "PREMIUM",
                    modifier = Modifier
                        .background(Gold.copy(alpha = 0.11f), RoundedCornerShape(50))
                        .border(1.dp, Gold.copy(alpha = 0.32f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
