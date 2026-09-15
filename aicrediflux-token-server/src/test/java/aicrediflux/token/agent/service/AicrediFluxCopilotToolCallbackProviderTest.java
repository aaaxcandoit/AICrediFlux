package aicrediflux.token.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.fasterxml.jackson.databind.ObjectMapper;

import aicrediflux.token.agent.domain.AgentToolLog;
import aicrediflux.token.agent.mapper.AgentRunMapper;
import aicrediflux.token.agent.mapper.AgentToolLogMapper;
import aicrediflux.token.mapper.UserMapper;
import aicrediflux.token.pojo.entity.User;

class AicrediFluxCopilotToolCallbackProviderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void auditedToolCallbackRedactsPayloadsAndPublishesLatency() {
        TestProvider provider = provider(null, new AdvancingClock());
        ToolCallback callback = provider.withAudit(secretEchoTool());
        ToolContext context = toolContext();

        String result = callback.call("{\"apiKey\":\"sk-secret-123456\",\"model\":\"deepseek-chat\"}", context);

        assertThat(result).contains("\"ok\":true").contains("visible").contains("***REDACTED***");
        assertThat(result).doesNotContain("secret-channel-key");
        ArgumentCaptor<AgentToolLog> insertCaptor = ArgumentCaptor.forClass(AgentToolLog.class);
        verify(provider.toolLogMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getInputJson()).contains("***REDACTED***");
        assertThat(insertCaptor.getValue().getInputJson()).doesNotContain("sk-secret-123456");

        ArgumentCaptor<AgentToolLog> updateCaptor = ArgumentCaptor.forClass(AgentToolLog.class);
        verify(provider.toolLogMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getOutputJson()).contains("***REDACTED***");
        assertThat(updateCaptor.getValue().getOutputJson()).doesNotContain("secret-channel-key");
        assertThat(updateCaptor.getValue().getFinishedAt()).isAfter(updateCaptor.getValue().getStartedAt());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(provider.publisher).toolResult(eq("AR001"), eq("secretEcho"), any(), eq(AgentToolLog.STATUS_SUCCEEDED), eventCaptor.capture());
        Map<String, Object> payload = (Map<String, Object>) eventCaptor.getValue();
        assertThat(payload).containsKey("latencyMs");
        assertThat(payload.get("result").toString()).contains("***REDACTED***");
        assertThat(payload.get("result").toString()).doesNotContain("secret-channel-key");
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedToolCallbackReturnsErrorEnvelopeAndRedactsErrorEvent() throws Exception {
        TestProvider provider = provider(null, new AdvancingClock());
        ToolCallback callback = provider.withAudit(failingTool());

        String result = callback.call("{}", toolContext());

        assertThat(JSON.readTree(result).path("ok").asBoolean()).isFalse();
        assertThat(result).contains("TOOL_ERROR").contains("***REDACTED***");
        assertThat(result).doesNotContain("sk-secret-123456");
        ArgumentCaptor<AgentToolLog> updateCaptor = ArgumentCaptor.forClass(AgentToolLog.class);
        verify(provider.toolLogMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo(AgentToolLog.STATUS_FAILED);
        assertThat(updateCaptor.getValue().getErrorMessage()).contains("***REDACTED***");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(provider.publisher).toolResult(eq("AR001"), eq("failingTool"), any(), eq(AgentToolLog.STATUS_FAILED), eventCaptor.capture());
        Map<String, Object> payload = (Map<String, Object>) eventCaptor.getValue();
        assertThat(payload).containsKey("latencyMs");
        assertThat(payload.get("error").toString()).contains("***REDACTED***");
    }

    @Test
    void permissionDeniedToolCallbackReturnsDeniedEnvelope() throws Exception {
        TestProvider provider = provider(null, new AdvancingClock());
        ToolCallback callback = provider.withAudit(permissionDeniedTool());

        String result = callback.call("{}", toolContext());

        assertThat(JSON.readTree(result).path("errorCode").asText()).isEqualTo("PERMISSION_DENIED");
        ArgumentCaptor<AgentToolLog> updateCaptor = ArgumentCaptor.forClass(AgentToolLog.class);
        verify(provider.toolLogMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo(AgentToolLog.STATUS_DENIED);
        verify(provider.publisher).toolResult(eq("AR001"), eq("channelStatus"), any(), eq(AgentToolLog.STATUS_DENIED), any());
    }

    @Test
    void auditedToolCallbackStoresValidJsonWhenResultIsLongerThanJsonColumnLimit() {
        TestProvider provider = provider(null, new AdvancingClock());
        ToolCallback callback = provider.withAudit(longJsonTool());

        callback.call("{\"query\":\"channel health\"}", toolContext());

        ArgumentCaptor<AgentToolLog> updateCaptor = ArgumentCaptor.forClass(AgentToolLog.class);
        verify(provider.toolLogMapper).updateById(updateCaptor.capture());
        String outputJson = updateCaptor.getValue().getOutputJson();
        assertThatCode(() -> JSON.readTree(outputJson)).doesNotThrowAnyException();
        assertThat(outputJson).contains("\"truncated\":true");
        assertThat(outputJson).contains("\"rawLength\":");
    }

    @Test
    void callbacksForUsesPolicyToFilterChannelStatus() {
        UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);
        User admin = new User();
        admin.setRole(3);
        when(userMapper.selectById(7)).thenReturn(admin);
        AicrediFluxToolPolicy policy = new AicrediFluxToolPolicy(userMapper);
        TestProvider provider = provider(policy, new AdvancingClock());
        AicrediFluxToolInvocationContext context = new AicrediFluxToolInvocationContext(7, "default", "AS001", "AR001");

        assertThat(provider.callbacksFor(context, "no available channel 一般怎么排查？"))
                .extracting(callback -> callback.getToolDefinition().name())
                .doesNotContain("channelStatus");
        assertThat(provider.callbacksFor(context, "当前渠道状态、成功率和 429 风险怎么样？"))
                .extracting(callback -> callback.getToolDefinition().name())
                .contains("channelStatus");
    }

    private TestProvider provider(AicrediFluxToolPolicy policy, Clock clock) {
        return new TestProvider(policy, clock);
    }

    private ToolContext toolContext() {
        return new ToolContext(Map.of(
                AicrediFluxGatewayChatModel.CTX_USER_ID, 7,
                AicrediFluxGatewayChatModel.CTX_GROUP, "default",
                AicrediFluxGatewayChatModel.CTX_SESSION_NO, "AS001",
                AicrediFluxGatewayChatModel.CTX_RUN_NO, "AR001",
                AicrediFluxGatewayChatModel.CTX_MODEL, "deepseek-v4-flash"));
    }

    private ToolCallback secretEchoTool() {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("secretEcho").description("测试脱敏").inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String toolInput) { return call(toolInput, null); }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return "{\"channelKey\":\"secret-channel-key\",\"summary\":\"visible\"}";
            }
        };
    }

    private ToolCallback failingTool() {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("failingTool").description("测试失败脱敏").inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String toolInput) { return call(toolInput, null); }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                throw new IllegalStateException("上游错误 sk-secret-123456");
            }
        };
    }

    private ToolCallback permissionDeniedTool() {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("channelStatus").description("测试权限").inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String toolInput) { return call(toolInput, null); }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                throw new IllegalStateException("ChannelStatusTool 仅管理员可用");
            }
        };
    }

    private ToolCallback longJsonTool() {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("longJson").description("测试长 JSON").inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String toolInput) { return call(toolInput, null); }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return "{\"items\":[\"" + "渠道状态正常，延迟 10ms。".repeat(220) + "\"]}";
            }
        };
    }

    private static final class TestProvider extends AicrediFluxCopilotToolCallbackProvider {
        private final AgentToolLogMapper toolLogMapper;
        private final AgentRunEventPublisher publisher;

        private TestProvider(AicrediFluxToolPolicy policy, Clock clock) {
            this(org.mockito.Mockito.mock(AgentToolLogMapper.class), org.mockito.Mockito.mock(AgentRunEventPublisher.class), policy, clock);
        }

        private TestProvider(AgentToolLogMapper toolLogMapper, AgentRunEventPublisher publisher,
                             AicrediFluxToolPolicy policy, Clock clock) {
            super(org.mockito.Mockito.mock(AicrediFluxCopilotTools.class), new AicrediFluxToolInvocationContextHolder(),
                    toolLogMapper, org.mockito.Mockito.mock(AgentRunMapper.class), publisher, policy, clock);
            this.toolLogMapper = toolLogMapper;
            this.publisher = publisher;
        }
    }

    private static final class AdvancingClock extends Clock {
        private Instant current = Instant.parse("2026-09-12T00:00:00Z");

        @Override
        public ZoneId getZone() { return ZoneId.of("UTC"); }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() {
            Instant value = current;
            current = current.plusMillis(25);
            return value;
        }
    }
}



