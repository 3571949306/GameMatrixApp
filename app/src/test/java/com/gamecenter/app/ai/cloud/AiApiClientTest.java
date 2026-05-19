package com.gamecenter.app.ai.cloud;

import com.gamecenter.app.ai.data.AiErrorCode;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiResult;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AiApiClientTest {

    private MockWebServer server;
    private AiApiClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/v1").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        AiProviderConfig config = new AiProviderConfig(
                "TestProvider", "test-model", "test-api-key",
                baseUrl, true, false, 32000, 1
        );
        client = new AiApiClient(config);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void chatSync_success_returnsContent() throws Exception {
        String responseBody = "{\"choices\":[{\"message\":{\"content\":\"Hello!\"}}]}";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseBody));

        AiResult result = client.chatSync("You are helpful", "Hi");

        assertTrue(result.success);
        assertEquals("Hello!", result.content);
        assertEquals("cloud", result.source);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().endsWith("/chat/completions"));
        assertNotNull(request.getHeader("Authorization"));
        assertTrue(request.getHeader("Authorization").startsWith("Bearer "));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"test-model\""));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("\"role\":\"user\""));
    }

    @Test
    public void chatSync_success_withoutSystemPrompt() throws Exception {
        String responseBody = "{\"choices\":[{\"message\":{\"content\":\"No system\"}}]}";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseBody));

        AiResult result = client.chatSync(null, "Hi");

        assertTrue(result.success);
        assertEquals("No system", result.content);

        String body = server.takeRequest().getBody().readUtf8();
        assertFalse(body.contains("\"role\":\"system\""));
    }

    @Test
    public void chatSync_httpError_returnsFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("Rate limited"));

        AiResult result = client.chatSync(null, "Hi");

        assertFalse(result.success);
        assertTrue(result.message.contains("429"));
        assertTrue(result.errorCode.startsWith(AiErrorCode.HTTP_ERROR));
    }

    @Test
    public void chatSync_serverError_returnsFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        AiResult result = client.chatSync(null, "Hi");

        assertFalse(result.success);
        assertTrue(result.message.contains("500"));
        assertTrue(result.errorCode.startsWith(AiErrorCode.HTTP_ERROR));
    }

    @Test
    public void chatSync_connectionFailure_returnsNetworkError() {
        AiProviderConfig badConfig = new AiProviderConfig(
                "Bad", "m", "k", "http://127.0.0.1:1", true, false, 100, 1
        );
        AiApiClient badClient = new AiApiClient(badConfig);

        AiResult result = badClient.chatSync(null, "Hi");

        assertFalse(result.success);
        assertEquals(AiErrorCode.NETWORK_ERROR, result.errorCode);
    }

    @Test
    public void chatSync_malformedJson_returnsNetworkError() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("not valid json"));

        AiResult result = client.chatSync(null, "Hi");

        assertFalse(result.success);
        assertEquals(AiErrorCode.NETWORK_ERROR, result.errorCode);
    }

    @Test
    public void chatSync_missingChoices_returnsNetworkError() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"error\":\"bad request\"}"));

        AiResult result = client.chatSync(null, "Hi");

        assertFalse(result.success);
        assertEquals(AiErrorCode.NETWORK_ERROR, result.errorCode);
    }

    @Test
    public void chatSync_emptySystemPrompt_skipsSystemMessage() throws Exception {
        String responseBody = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseBody));

        AiResult result = client.chatSync("", "Hi");

        assertTrue(result.success);
        String body = server.takeRequest().getBody().readUtf8();
        assertFalse(body.contains("\"role\":\"system\""));
    }
}
