package com.gamecenter.app.network

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RelayHttpClientTest {
    
    @Test
    fun `getWebSocketUrl generates correct url for host`() {
        val baseUrl = "https://example.com/api/ddz-relay"
        val roomCode = "ABC123"
        val hostToken = "token123"
        
        val url = RelayHttpClient.getWebSocketUrl(baseUrl, roomCode, hostToken)
        
        assertTrue(url.contains("room=ABC123"))
        assertTrue(url.contains("role=host"))
        assertTrue(url.contains("token=token123"))
    }
    
    @Test
    fun `getWebSocketClientUrl generates correct url for client`() {
        val baseUrl = "https://example.com/api/ddz-relay"
        val roomCode = "ABC123"
        
        val url = RelayHttpClient.getWebSocketClientUrl(baseUrl, roomCode)
        
        assertTrue(url.contains("room=ABC123"))
        assertTrue(url.contains("role=client"))
        assertFalse(url.contains("token="))
    }
    
    @Test
    fun `url encoding handles special characters`() {
        val roomCode = "AB C+123"
        
        val url = RelayHttpClient.getWebSocketClientUrl(
            "https://example.com/api/ddz-relay",
            roomCode
        )
        
        assertTrue(url.contains("AB+C%2B123") || url.contains("AB+C+123"))
    }
    
    @Test
    fun `http url is converted to ws`() {
        val url = RelayHttpClient.getWebSocketClientUrl(
            "http://example.com/api/ddz-relay",
            "TEST"
        )
        
        assertTrue(url.startsWith("ws://") || url.startsWith("wss://"))
    }
    
    @Test
    fun `https url is converted to wss`() {
        val url = RelayHttpClient.getWebSocketClientUrl(
            "https://example.com/api/ddz-relay",
            "TEST"
        )
        
        assertTrue(url.startsWith("wss://"))
    }

    @Test
    fun `host url includes token and role`() {
        val url = RelayHttpClient.getWebSocketUrl(
            "https://example.com/api/ddz-relay",
            "ROOM42",
            "host token"
        )

        assertTrue(url.contains("room=ROOM42"))
        assertTrue(url.contains("role=host"))
        assertTrue(url.contains("token=host+token") || url.contains("token=host%20token"))
    }

    @Test
    fun `client url omits token parameter`() {
        val url = RelayHttpClient.getWebSocketClientUrl(
            "https://example.com/api/ddz-relay",
            "ROOM42"
        )

        assertTrue(url.contains("role=client"))
        assertFalse(url.contains("token="))
    }
}
