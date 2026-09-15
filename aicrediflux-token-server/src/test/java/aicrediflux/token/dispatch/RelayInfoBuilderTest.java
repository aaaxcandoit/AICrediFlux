package aicrediflux.token.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import aicrediflux.token.constant.ContextKeyConstants;
import aicrediflux.token.relay.common.RelayInfo;

class RelayInfoBuilderTest {

    private final RelayInfoBuilder builder = new RelayInfoBuilder();

    @Test
    void buildsApiRelayInfoWithDispatchMetadata() {
        MockHttpServletRequest request = jsonRequest("/v1/chat/completions");
        request.setAttribute("request_id", "req-api");
        request.setAttribute("id", 11);
        request.setAttribute("token_id", 22);
        request.setAttribute("token_key", "sk-test");
        request.setAttribute("token_group", "default");
        request.setAttribute("username", "alice");
        request.setAttribute(ContextKeyConstants.CHANNEL_ID, 7);
        request.setAttribute(ContextKeyConstants.CHANNEL_NAME, "mock-channel");
        request.setAttribute(ContextKeyConstants.CHANNEL_TYPE, 1);
        request.setAttribute(ContextKeyConstants.PARSED_REQUEST_BODY, Map.of("model", "qwen-plus"));

        DispatchContext context = DispatchContext.api(11, "default", "req-api")
                .tokenId(22)
                .sessionId("session-1")
                .runId("run-1");
        RelayInfo info = builder.buildApi(request, context);

        assertThat(info.getUserId()).isEqualTo(11);
        assertThat(info.getTokenId()).isEqualTo(22);
        assertThat(info.getRequestId()).isEqualTo("req-api");
        assertThat(info.getOriginModelName()).isEqualTo("qwen-plus");
        assertThat(info.getExtraData())
                .containsEntry("dispatch_source", "API")
                .containsEntry("dispatch_session_id", "session-1")
                .containsEntry("dispatch_run_id", "run-1")
                .containsEntry("username", "alice")
                .containsEntry("channelName", "mock-channel");
    }

    @Test
    void buildsPlaygroundRelayInfoAsTemporaryUnlimitedToken() {
        MockHttpServletRequest request = jsonRequest("/pg/chat/completions");
        request.setAttribute("id", 33);
        request.setAttribute("username", "bob");
        request.setAttribute("group", "vip");
        request.setAttribute(ContextKeyConstants.USING_GROUP, "vip");
        request.setAttribute(ContextKeyConstants.PARSED_REQUEST_BODY, Map.of("model", "deepseek-chat"));

        DispatchContext context = DispatchContext.playground(33, "vip", "req-pg");
        RelayInfo info = builder.buildPlayground(request, context);

        assertThat(info.isPlayground()).isTrue();
        assertThat(info.getRequestURLPath()).isEqualTo("/v1/chat/completions");
        assertThat(info.getTokenId()).isZero();
        assertThat(info.getTokenKey()).isEqualTo("playground-vip");
        assertThat(info.isTokenUnlimited()).isTrue();
        assertThat(info.getOriginModelName()).isEqualTo("deepseek-chat");
        assertThat(info.getExtraData()).containsEntry("dispatch_source", "PLAYGROUND");
    }

    @Test
    void normalizesOpenAiCompatibleBaseUrlEndingWithV1() {
        MockHttpServletRequest request = jsonRequest("/v1/embeddings");
        request.setAttribute(ContextKeyConstants.CHANNEL_BASE_URL, "https://api.siliconflow.cn/v1");
        request.setAttribute(ContextKeyConstants.CHANNEL_TYPE, 1);
        request.setAttribute(ContextKeyConstants.PARSED_REQUEST_BODY, Map.of("model", "Qwen/Qwen3-Embedding-0.6B"));

        RelayInfo info = builder.buildApi(request, DispatchContext.api(1, "default", "req-rag"));

        assertThat(info.getChannelBaseUrl()).isEqualTo("https://api.siliconflow.cn");
    }
    private MockHttpServletRequest jsonRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json");
        return request;
    }
}
