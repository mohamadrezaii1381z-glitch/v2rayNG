package com.v2ray.ang.dto

/**
 * Monotonic traffic totals of the current tunnel session, split by outbound kind.
 *
 * Only [proxyUp] and [proxyDown] describe the tunnelled connection; the direct counters
 * exist so the daemon keeps a single source of truth for both notification and UI.
 */
data class TrafficTotals(
    val proxyUp: Long = 0L,
    val proxyDown: Long = 0L,
    val directUp: Long = 0L,
    val directDown: Long = 0L,
)
