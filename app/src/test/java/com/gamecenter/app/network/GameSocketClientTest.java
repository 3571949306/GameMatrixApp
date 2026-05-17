package com.gamecenter.app.network;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GameSocketClientTest {

    @Test
    public void extractTokenFromUrl_decodesTokenParameter() {
        assertEquals("abc def", GameSocketClient.extractTokenFromUrl("wss://server/ws?room=ABC&token=abc%20def"));
        assertEquals("tok", GameSocketClient.extractTokenFromUrl("wss://server/ws?token=tok&room=ABC"));
        assertEquals("", GameSocketClient.extractTokenFromUrl("wss://server/ws?token=&room=ABC"));
        assertNull(GameSocketClient.extractTokenFromUrl("wss://server/ws?room=ABC"));
        assertNull(GameSocketClient.extractTokenFromUrl(null));
    }

    @Test
    public void offerPendingMessage_keepsConfiguredMaximum() throws Exception {
        Queue<JSONObject> queue = new ArrayDeque<>();

        WebSocketClientHelper.offerPendingMessage(queue, new JSONObject().put("id", 1), 2);
        WebSocketClientHelper.offerPendingMessage(queue, new JSONObject().put("id", 2), 2);
        WebSocketClientHelper.offerPendingMessage(queue, new JSONObject().put("id", 3), 2);

        assertEquals(2, queue.size());
        assertEquals(2, queue.poll().getInt("id"));
        assertEquals(3, queue.poll().getInt("id"));
    }

}
