package com.v2ray.ang.ui.hardvpn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import java.util.Locale

/*
 * HardVPN visual identity, transcribed from the supplied design source.
 * Keep these values in one place: every HardVPN surface must stay visually consistent.
 */

val Navy = Color(0xFF070F1D)
val CardColor = Color(0xFF152235)
val SelectedCardColor = Color(0xFF172A44)
val Muted = Color(0xFFB0BFD2)
val ElectricBlue = Color(0xFF1772FF)
val LightBlue = Color(0xFF9BC5FF)
val HardGreen = Color(0xFF28D79C)
val Gold = Color(0xFFF4D28A)

val CardShape = RoundedCornerShape(18.dp)
val CardBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))

/** Accent used by the "download" statistic card. */
val UploadAccent = Color(0xFF99B3D3)

/** Power button animation timings, kept from the design source. */
const val POWER_COLOR_MS = 600
const val RING_ROTATION_MS = 1_750
const val RING_FADE_MS = 300

/** Minimum delay between two traffic samples; the daemon is not polled while hidden. */
const val TRAFFIC_SAMPLE_INTERVAL_MS = 2_000L

private val CombiningMarks = Regex("\\p{M}+")

/**
 * Locale-insensitive search key. Built once per row so filtering hundreds of
 * servers never re-normalises text during scrolling.
 */
fun searchKey(value: String): String = CombiningMarks.replace(
    Normalizer.normalize(value, Normalizer.Form.NFD), ""
).lowercase(Locale.ROOT)

/** Human readable speed; never shows a fabricated value, "--" when there is no sample. */
fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = bytesPerSecond.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${value.toLong()} ${units[index]}"
    else String.format(Locale.US, "%.1f %s", value, units[index])
}

/** Session totals, shown small under the speed cards. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${value.toLong()} ${units[index]}"
    else String.format(Locale.US, "%.1f %s", value, units[index])
}

fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        safe / 3600L,
        (safe % 3600L) / 60L,
        safe % 60L
    )
}

fun pingLabel(pingMs: Long?): String =
    pingMs?.takeIf { it > 0L }?.let { "$it ms" } ?: "--"

fun pingColor(pingMs: Long?): Color = when {
    pingMs == null || pingMs <= 0L -> Muted
    pingMs < 60L -> HardGreen
    pingMs < 120L -> LightBlue
    else -> Gold
}

/** Four-bar signal meter driven by the measured ping. */
fun activeSignalBars(pingMs: Long?): Int = when {
    pingMs == null || pingMs <= 0L -> 0
    pingMs < 60L -> 4
    pingMs < 100L -> 3
    pingMs < 150L -> 2
    else -> 1
}

/** Typography scale used by the HardVPN shell. */
val HeaderTitleSize = 20.sp
val SectionLabelSize = 11.sp
val BodySize = 14.sp
val CaptionSize = 11.sp
