package com.v2ray.ang.ui.hardvpn.data

import com.v2ray.ang.ui.hardvpn.ConfigDomain
import com.v2ray.ang.ui.hardvpn.ConnectionPhase
import java.util.concurrent.atomic.AtomicLong

/** The section that currently owns the single HardVPN-managed connection. */
enum class ConnectionOwner { None, Home, Explore, Manual, Dns }

fun ConfigDomain.toOwner(): ConnectionOwner = when (this) {
    ConfigDomain.Home -> ConnectionOwner.Home
    ConfigDomain.Explore -> ConnectionOwner.Explore
    ConfigDomain.Manual -> ConnectionOwner.Manual
}

/**
 * Centralized, serialized connection arbitration for all HardVPN sections.
 *
 * Only one transition may run at a time: the ViewModel's connect guard admits a single
 * request, and every request receives a monotonically increasing operation id. Callbacks
 * belonging to an older id must be ignored by callers, which prevents stale engine events
 * from overwriting a newer request.
 *
 * This coordinator owns *ownership and sequencing only*. It does not merge domain data,
 * does not create a second VPN engine, and never fabricates a connection state: the actual
 * phase always comes from daemon broadcasts handled by the ViewModel.
 */
object HardVpnConnectionCoordinator {

    private val operationCounter = AtomicLong(0L)

    @Volatile
    var activeOwner: ConnectionOwner = ConnectionOwner.None
        private set

    @Volatile
    var pendingOwner: ConnectionOwner = ConnectionOwner.None
        private set

    /** Starts a new operation and invalidates every older pending callback. */
    fun nextOperation(): Long = operationCounter.incrementAndGet()

    fun isCurrent(operationId: Long): Boolean = operationCounter.get() == operationId

    fun currentOperation(): Long = operationCounter.get()

    fun markActive(owner: ConnectionOwner) {
        activeOwner = owner
        if (pendingOwner == owner) pendingOwner = ConnectionOwner.None
    }

    fun markPending(owner: ConnectionOwner) {
        pendingOwner = owner
    }

    fun markIdle() {
        activeOwner = ConnectionOwner.None
        pendingOwner = ConnectionOwner.None
    }

    /** Owner derived from the domain that the running engine configuration belongs to. */
    fun ownerFor(domain: ConfigDomain): ConnectionOwner = domain.toOwner()

    fun isTransitionBusy(phase: ConnectionPhase): Boolean =
        phase == ConnectionPhase.Connecting || phase == ConnectionPhase.Disconnecting
}
