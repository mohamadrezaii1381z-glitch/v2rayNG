package com.v2ray.ang.hardvpn.ads

/**
 * AdMob integration boundary.
 *
 * Ads are intentionally disabled in this build: there are no production ad unit IDs, and adding
 * the SDK without them would ship an unconfigured third-party dependency. When ads are enabled,
 * the implementation behind this interface must use official Google test units during development,
 * keep production IDs outside source control, apply frequency caps, and follow AdMob policy.
 *
 * Hard rules for any future implementation:
 * - never block, delay, or cancel [com.v2ray.ang.hardvpn.HardVpnViewModel]'s connection path;
 * - never show an interstitial or rewarded ad while a tunnel is connected or connecting;
 * - never turn an ad failure into a connection failure.
 *
 * Every method must be synchronous, non-throwing, and free of I/O. The current gate is a no-op, so
 * the call sites below can be wired before an SDK exists without changing connection behavior.
 */
interface HardVpnAdGate {
    /** A connection was requested by the user. Implementations may only record a counter here. */
    fun onConnectRequested()

    /** The daemon confirmed a live tunnel. Implementations may reset a frequency counter here. */
    fun onConnectionEstablished()
}

object DisabledHardVpnAdGate : HardVpnAdGate {
    override fun onConnectRequested() = Unit
    override fun onConnectionEstablished() = Unit
}

object HardVpnAds {
    @Volatile
    var gate: HardVpnAdGate = DisabledHardVpnAdGate
}
