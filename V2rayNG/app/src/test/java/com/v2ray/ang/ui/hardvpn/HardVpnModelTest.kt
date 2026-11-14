package com.v2ray.ang.ui.hardvpn

import com.v2ray.ang.ui.hardvpn.data.ConnectionOwner
import com.v2ray.ang.ui.hardvpn.data.HardVpnConnectionCoordinator
import com.v2ray.ang.ui.hardvpn.data.toOwner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-JVM regression tests for the HardVPN areas that previously produced Kotlin
 * compilation errors and false country detections:
 *  - prefix-only country detection in [detectCountryCode] (braced interpolation),
 *  - [ConfigDomain.toOwner] ownership mapping of the centralized coordinator.
 */
class HardVpnModelTest {

    @Test
    fun country_prefix_name_and_aliases() {
        assertEquals("DE", detectCountryCode("Germany 01"))
        assertEquals("DE", detectCountryCode("germany-frankfurt"))
        assertEquals("FR", detectCountryCode("France Paris"))
        assertEquals("US", detectCountryCode("United States New York"))
        assertEquals("US", detectCountryCode("USA 03"))
        assertEquals("US", detectCountryCode("US-East"))
        assertEquals("GB", detectCountryCode("United Kingdom London"))
        assertEquals("GB", detectCountryCode("UK 01"))
        assertEquals("TR", detectCountryCode("Türkiye Istanbul"))
        assertEquals("TR", detectCountryCode("Turkey 02"))
        assertEquals("NL", detectCountryCode("[NL] Amsterdam"))
        assertEquals("JP", detectCountryCode("Japan Tokyo"))
        assertEquals("DE", detectCountryCode("DE-01"))
    }

    @Test
    fun country_flag_emoji_at_start() {
        assertEquals("DE", detectCountryCode("🇩🇪 Germany"))
        assertEquals("FR", detectCountryCode("🇫🇷 Paris"))
    }

    @Test
    fun country_not_detected_from_middle_of_name() {
        // A country word in the middle of an unrelated remark must not be detected.
        assertNull(detectCountryCode("Fast Germany Server"))
        assertNull(detectCountryCode("Premium France Node"))
        assertNull(detectCountryCode("My US Proxy"))
        assertNull(detectCountryCode("Server 01"))
        assertNull(detectCountryCode(""))
    }

    @Test
    fun owner_mapping_for_all_domains() {
        assertEquals(ConnectionOwner.Home, ConfigDomain.Home.toOwner())
        assertEquals(ConnectionOwner.Explore, ConfigDomain.Explore.toOwner())
        assertEquals(ConnectionOwner.Manual, ConfigDomain.Manual.toOwner())
        assertEquals(ConnectionOwner.Home, HardVpnConnectionCoordinator.ownerFor(ConfigDomain.Home))
    }

    @Test
    fun coordinator_operation_ids_invalidate_stale_callbacks() {
        val first = HardVpnConnectionCoordinator.nextOperation()
        assertTrue(HardVpnConnectionCoordinator.isCurrent(first))
        val second = HardVpnConnectionCoordinator.nextOperation()
        assertFalse(HardVpnConnectionCoordinator.isCurrent(first))
        assertTrue(HardVpnConnectionCoordinator.isCurrent(second))
        assertTrue(second > first)
    }

    @Test
    fun country_display_names() {
        assertEquals("Germany", countryDisplayName("DE"))
        assertEquals("United States", countryDisplayName("US"))
        assertNull(countryDisplayName(null))
    }
}
