package com.v2ray.ang.ui.hardvpn

import com.v2ray.ang.ui.hardvpn.data.HardVpnEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.Serializable

/**
 * The HardVPN broadcast receiver shares its broadcast action with upstream
 * messages whose `content` extra is a Serializable result object rather than
 * a String. Reading it with `getStringExtra` crashed on device with
 * ClassCastException (Parcel.LazyValue). The receiver must keep genuine
 * String payloads intact and never throw on other payload types.
 */
class HardVpnEngineExtraTest {

    private data class FakeSerializablePayload(val value: Int) : Serializable

    @Test
    fun stringPayloadIsReturnedIntact() {
        val failure = "failed to open geosite.dat"
        assertEquals(failure, HardVpnEngine.stringExtraValue(failure))
        assertEquals("1024,2048", HardVpnEngine.stringExtraValue("1024,2048"))
        assertEquals("", HardVpnEngine.stringExtraValue(""))
    }

    @Test
    fun nonStringPayloadsMapToEmptyWithoutThrowing() {
        assertEquals("", HardVpnEngine.stringExtraValue(FakeSerializablePayload(42)))
        assertEquals("", HardVpnEngine.stringExtraValue(1234))
        assertEquals("", HardVpnEngine.stringExtraValue(listOf("a", "b")))
        assertEquals("", HardVpnEngine.stringExtraValue(Any()))
    }

    @Test
    fun missingPayloadMapsToEmpty() {
        assertEquals("", HardVpnEngine.stringExtraValue(null))
    }
}
