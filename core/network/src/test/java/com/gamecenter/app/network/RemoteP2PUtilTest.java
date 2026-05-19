package com.gamecenter.app.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteP2PUtilTest {

    @Test
    public void normalizeRoomCode_filtersAndUppercasesUsingProductionAlphabet() {
        assertEquals("ABC234", RemoteP2PUtil.normalizeRoomCode("ddz://a!b@c#234xyz"));
        assertEquals("ABC", RemoteP2PUtil.normalizeRoomCode("ABC010"));
        assertEquals("", RemoteP2PUtil.normalizeRoomCode(null));
    }

    @Test
    public void isValidRoomCode_requiresSixAllowedCharacters() {
        assertTrue(RemoteP2PUtil.isValidRoomCode("ABC234"));
        assertFalse(RemoteP2PUtil.isValidRoomCode("ABC"));
        assertFalse(RemoteP2PUtil.isValidRoomCode("ABC010"));
        assertFalse(RemoteP2PUtil.isValidRoomCode(null));
    }

    @Test
    public void findRoomCode_extractsFromInviteText() {
        assertEquals("ABC234", RemoteP2PUtil.findRoomCode("斗地主云房间 ABC234，快来加入"));
        assertEquals("ABC234", RemoteP2PUtil.findRoomCode("DDZ://ABC234"));
        assertEquals("", RemoteP2PUtil.findRoomCode("没有房间码"));
    }

    @Test
    public void formatRelayInvite_requiresValidRoomCode() {
        String invite = RemoteP2PUtil.formatRelayInvite("abc234");

        assertTrue(invite.contains("ABC234"));
        assertEquals("", RemoteP2PUtil.formatRelayInvite("ABC"));
    }

    @Test
    public void parseEndpoint_acceptsIpv4DomainAndIpv6() {
        RemoteP2PUtil.Endpoint ipv4 = RemoteP2PUtil.parseEndpoint("p2p://192.168.1.2:8765", 1111);
        RemoteP2PUtil.Endpoint domain = RemoteP2PUtil.parseEndpoint("example.com", 8765);
        RemoteP2PUtil.Endpoint ipv6 = RemoteP2PUtil.parseEndpoint("[2001:db8::1]:9999", 8765);

        assertNotNull(ipv4);
        assertEquals("192.168.1.2", ipv4.host);
        assertEquals(8765, ipv4.port);
        assertNotNull(domain);
        assertEquals("example.com", domain.host);
        assertEquals(8765, domain.port);
        assertNotNull(ipv6);
        assertEquals("2001:db8::1", ipv6.host);
        assertEquals(9999, ipv6.port);
    }

    @Test
    public void parseEndpoint_rejectsLoopbackAndInvalidPorts() {
        assertNull(RemoteP2PUtil.parseEndpoint("127.0.0.1:8765", 8765));
        assertNull(RemoteP2PUtil.parseEndpoint("192.168.1.2:99999", 99999));
    }

    @Test
    public void formatInviteAddress_formatsIpv4AndIpv6() {
        assertEquals("p2p://192.168.1.2:8765",
                RemoteP2PUtil.formatInviteAddress("192.168.1.2", 8765));
        assertEquals("p2p://[2001:db8::1]:8765",
                RemoteP2PUtil.formatInviteAddress("2001:db8::1", 8765));
        assertEquals("", RemoteP2PUtil.formatInviteAddress("127.0.0.1", 8765));
    }

    @Test
    public void buildWebSocketUrl_addsSchemeAndParameters() {
        String url = RemoteP2PUtil.buildWebSocketUrl("relay.example.com", "DDZ", "ABC234", "tok", "Alice");

        assertTrue(url.startsWith("wss://relay.example.com/"));
        assertTrue(url.contains("game=DDZ"));
        assertTrue(url.contains("room=ABC234"));
        assertTrue(url.contains("token=tok"));
        assertTrue(url.contains("name=Alice"));
    }
}
