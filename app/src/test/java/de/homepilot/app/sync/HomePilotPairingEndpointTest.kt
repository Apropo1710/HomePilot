package de.homepilot.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePilotPairingEndpointTest {
    @Test fun acceptsPrivateIpv4AndLocalIpv6() {
        assertTrue(HomePilotPairingEndpoint.isAllowed("192.168.1.25", 43210))
        assertTrue(HomePilotPairingEndpoint.isAllowed("10.0.0.2", 43210))
        assertTrue(HomePilotPairingEndpoint.isAllowed("172.20.1.4", 43210))
        assertTrue(HomePilotPairingEndpoint.isAllowed("172.31.255.254", 43210))
        assertTrue(HomePilotPairingEndpoint.isAllowed("169.254.1.4", 43210))
        assertTrue(HomePilotPairingEndpoint.isAllowed("fd12:3456::1", 43210))
        assertTrue(HomePilotPairingEndpoint.isAllowed("fe80::1", 43210))
    }

    @Test fun rejectsPublicAndSpecialAddresses() {
        assertFalse(HomePilotPairingEndpoint.isAllowed("8.8.8.8", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("172.32.0.1", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("255.255.255.255", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("127.0.0.1", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("0.0.0.0", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("224.0.0.1", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("::1", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("ff02::1", 43210))
    }

    @Test fun rejectsDnsNamesMalformedAddressesAndPorts() {
        assertFalse(HomePilotPairingEndpoint.isAllowed("example.com", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("192.168.1.999", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("192.168.001.2", 43210))
        assertFalse(HomePilotPairingEndpoint.isAllowed("192.168.1.2", 0))
        assertFalse(HomePilotPairingEndpoint.isAllowed("192.168.1.2", 65536))
    }
}
