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

/**
 * AI云端API客户端的单元测试类。
 *
 * 这个测试类使用MockWebServer（模拟服务器）来测试AiApiClient的行为。
 * 为什么要用模拟服务器？因为我们在测试时不希望真的去调用远程API，
 * 那样会依赖网络、消耗API额度、而且响应时间不可控。
 * 模拟服务器可以让我们精确控制服务器返回的内容，从而测试各种场景。
 *
 * 本测试类主要测试：
 * 1. 正常的聊天请求 —— 服务器返回正确响应时，客户端能否正确解析
 * 2. HTTP错误 —— 服务器返回429（限流）、500（服务器错误）等
 * 3. 网络连接失败 —— 无法连接到服务器的情况
 * 4. 响应格式异常 —— 服务器返回的不是合法JSON或缺少必要字段
 * 5. 系统提示词（system prompt）的发送逻辑
 */
public class AiApiClientTest {

    private MockWebServer server;
    private AiApiClient client;

    @Before
    public void setUp() throws Exception {
        // 每个测试方法执行前都会调用这个方法
        // 启动模拟服务器，并创建一个连接到该服务器的AiApiClient
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
        // 每个测试方法执行后都会调用这个方法
        // 关闭模拟服务器，释放资源
        server.shutdown();
    }

    @Test
    public void chatSync_success_returnsContent() throws Exception {
        // 测试：服务器返回正常响应时，客户端应正确提取AI回复内容
        // 同时验证请求中包含了正确的模型名、系统提示词和用户消息
        String responseBody = "{\"choices\":[{\"message\":{\"content\":\"Hello!\"}}]}";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseBody));

        AiResult result = client.chatSync("You are helpful", "Hi");

        assertTrue(result.success);
        assertEquals("Hello!", result.content);
        assertEquals("cloud", result.source);

        // 验证发送的HTTP请求格式是否正确
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().endsWith("/chat/completions"));
        assertNotNull(request.getHeader("Authorization"));
        assertTrue(request.getHeader("Authorization").startsWith("Bearer "));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"test-model\""));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"max_tokens\":2048"));
    }

    @Test
    public void chatSync_success_withoutSystemPrompt() throws Exception {
        // 测试：不提供系统提示词时，请求体中不应包含system角色的消息
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
        // 测试：服务器返回429（请求过多/限流）时，应返回失败结果并附带HTTP错误码
        server.enqueue(new MockResponse().setResponseCode(429).setBody("Rate limited"));

        AiResult result = client.chatSync(null, "Hi");

        assertFalse(result.success);
        assertTrue(result.message.contains("429"));
        assertTrue(result.errorCode.startsWith(AiErrorCode.HTTP_ERROR));
    }

    @Test
    public void chatSync_serverError_returnsFailure() throws Exception {
        // 测试：服务器返回500（内部错误）时，应返回失败结果
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        AiResult result = client.chatSync(null, "Hi");

        assertFalse(result.success);
        assertTrue(result.message.contains("500"));
        assertTrue(result.errorCode.startsWith(AiErrorCode.HTTP_ERROR));
    }

    @Test
    public void chatSync_connectionFailure_returnsNetworkError() {
        // 测试：无法连接到服务器时（比如端口不存在），应返回NETWORK_ERROR
        // 这里使用一个不可能连通的地址来模拟网络连接失败
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
        // 测试：服务器返回的不是合法JSON时，应返回NETWORK_ERROR
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("not valid json"));

        AiResult result = client.chatSync(null, "Hi");

        assertFalse(result.success);
        assertEquals(AiErrorCode.NETWORK_ERROR, result.errorCode);
    }

    @Test
    public void chatSync_missingChoices_returnsNetworkError() throws Exception {
        // 测试：服务器返回的JSON中没有choices字段时，应返回NETWORK_ERROR
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"error\":\"bad request\"}"));

        AiResult result = client.chatSync(null, "Hi");

        assertFalse(result.success);
        assertEquals(AiErrorCode.NETWORK_ERROR, result.errorCode);
    }

    @Test
    public void chatSync_emptySystemPrompt_skipsSystemMessage() throws Exception {
        // 测试：系统提示词为空字符串时，不应发送system角色的消息
        String responseBody = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseBody));

        AiResult result = client.chatSync("", "Hi");

        assertTrue(result.success);
        String body = server.takeRequest().getBody().readUtf8();
        assertFalse(body.contains("\"role\":\"system\""));
    }

    @Test
    public void chatSync_highCapabilityModel_usesLongerOutputLimit() throws Exception {
        AiProviderConfig longConfig = new AiProviderConfig(
                "TestProvider", "long-model", "test-api-key",
                server.url("/long").toString().replaceAll("/$", ""), true, false, 128000, 3
        );
        AiApiClient longClient = new AiApiClient(longConfig);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"choices\":[{\"message\":{\"content\":\"long ok\"}}]}"));

        AiResult result = longClient.chatSync(null, "Hi");

        assertTrue(result.success);
        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"max_tokens\":4096"));
    }
}
