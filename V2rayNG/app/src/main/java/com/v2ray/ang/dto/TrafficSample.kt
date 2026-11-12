package com.v2ray.ang.dto

import java.io.Serializable

/**
 * One real traffic sample measured by the daemon over [intervalMillis].
 *
 * The counter is owned by the running core and reset on every query, so both byte counts are
 * deltas. No UI process code may query the core directly; this payload is the only UI-facing
 * traffic channel.
 *
 * @param uplinkBytes bytes sent during the sample window.
 * @param downlinkBytes bytes received during the sample window.
 * @param intervalMillis elapsed time since the previous sample, 0 when no window exists yet.
 * @param connectedAtMillis wall-clock time the running core was started, 0 when it is not running.
 */
data class TrafficSample(
    val uplinkBytes: Long = 0,
    val downlinkBytes: Long = 0,
    val intervalMillis: Long = 0,
    val connectedAtMillis: Long = 0,
) : Serializable
