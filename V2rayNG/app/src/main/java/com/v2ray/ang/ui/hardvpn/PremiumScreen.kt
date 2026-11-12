package com.v2ray.ang.ui.hardvpn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Premium plans placeholder.
 *
 * Payment is deliberately deferred: no prices, no checkout, no entitlement state and no fake
 * subscription are shown. The screen only reserves the gold entry point from Home.
 */
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            ScreenHeader(
                title = "Premium",
                subtitle = "Plans are not available yet",
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                HardVpnCard(
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Gold.copy(alpha = 0.35f)
                    ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        Gold.copy(alpha = 0.14f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        Gold.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                DrawIcon(AppIcon.Bolt, Gold, Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HardVPN Premium",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "Payment integration is deferred until the free app is complete.",
                                    color = Muted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                    }
                }

                PremiumPlanCard(
                    title = "Monthly",
                    subtitle = "Pricing not configured",
                )
                PremiumPlanCard(
                    title = "Yearly",
                    subtitle = "Pricing not configured",
                )

                EmptyState(
                    icon = AppIcon.Lock,
                    title = "No plans to buy yet",
                    body = "This screen is a placeholder for the future payment boundary. Nothing here can be purchased, and no Premium state is claimed.",
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PremiumPlanCard(title: String, subtitle: String) {
    HardVpnCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                text = "Coming soon",
                onClick = {},
                enabled = false,
                outlined = true,
            )
        }
    }
}

/**
 * Sponsors & support placeholder.
 *
 * No sponsor name, image, link or payment address is invented. The slots stay empty until the
 * real information is supplied.
 */
@Composable
fun SponsorsScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            ScreenHeader(
                title = "Sponsors & Support",
                subtitle = "Sponsor slots are ready, none configured yet",
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                HardVpnCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        Gold.copy(alpha = 0.12f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                DrawIcon(AppIcon.Heart, Gold, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Thank you for your support",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "Sponsor names, images and support links will appear here once they are provided.",
                                    color = Muted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                    }
                }
                EmptyState(
                    icon = AppIcon.Heart,
                    title = "No sponsors yet",
                    body = "HardVPN never invents sponsor identities, links or payment addresses. This section stays empty until real data is supplied.",
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
