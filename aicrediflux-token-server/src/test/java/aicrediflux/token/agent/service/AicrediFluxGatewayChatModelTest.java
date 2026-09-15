package aicrediflux.token.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class AicrediFluxGatewayChatModelTest {
    @Test
    void promptIsConvertedToInternalRequestWithAgentContext() {
        InternalChatService internal = org.mockito.Mockito.mock(InternalChatService.class);
        AicrediFluxChatUsageHolder usageHolder = new AicrediFluxChatUsageHolder();
        when(internal.chat(any(InternalChatRequest.class))).thenReturn(
                new InternalChatResult("ok", 11, 7, 18L, "req-1", 9));
        AicrediFluxGatewayChatModel model = new AicrediFluxGatewayChatModel(internal, usageHolder);

        ChatResponse response = model.call(prompt(List.of(new UserMessage("hello")), List.of()));

        ArgumentCaptor<InternalChatRequest> captor = ArgumentCaptor.forClass(InternalChatRequest.class);
        verify(internal).chat(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7);
        assertThat(captor.getValue().sessionNo()).isEqualTo("AS001");
        assertThat(captor.getValue().runNo()).isEqualTo("AR001");
        assertThat(captor.getValue().model()).isEqualTo("deepseek-chat");
        assertThat(captor.getValue().messages()).extracting(InternalChatMessage::content).containsExactly("hello");
        assertThat(response.getResult().getOutput().getText()).isEqualTo("ok");
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(11);
        assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(7);
        assertThat(response.getMetadata().<Long>get(AicrediFluxGatewayChatModel.META_QUOTA)).isEqualTo(18L);
    }

    @Test
    void springAiToolCallingManagerExecutesToolAndReturnsToolResponseToSecondModelCall() {
        InternalChatService internal = org.mockito.Mockito.mock(InternalChatService.class);
        AicrediFluxChatUsageHolder usageHolder = new AicrediFluxChatUsageHolder();
        ToolCallback wallet = walletTool();
        when(internal.chat(any(InternalChatRequest.class)))
                .thenReturn(new InternalChatResult("", 10, 0, 10L, "req-1", 9,
                        List.of(new InternalChatToolCall("call-wallet-1", "function", "walletBalance", "{}"))))
                .thenReturn(new InternalChatResult("你当前还有 128000。", 16, 8, 24L, "req-1", 9));
        AicrediFluxGatewayChatModel model = new AicrediFluxGatewayChatModel(internal, usageHolder);

        ChatResponse response = model.call(prompt(List.of(new UserMessage("我还有多少 AI Credit？")), List.of(wallet)));

        ArgumentCaptor<InternalChatRequest> captor = ArgumentCaptor.forClass(InternalChatRequest.class);
        verify(internal, times(2)).chat(captor.capture());
        assertThat(captor.getAllValues().get(0).toolCallbacks()).containsExactly(wallet);
        assertThat(captor.getAllValues().get(1).messages())
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo("tool");
                    assertThat(message.toolCallId()).isEqualTo("call-wallet-1");
                    assertThat(message.content()).contains("128000");
                });
        assertThat(response.getResult().getOutput().getText()).isEqualTo("你当前还有 128000。");
        assertThat(usageHolder.current().promptTokens()).isEqualTo(26);
        assertThat(usageHolder.current().completionTokens()).isEqualTo(8);
        assertThat(usageHolder.current().quota()).isEqualTo(34L);
    }

    private Prompt prompt(List<org.springframework.ai.chat.messages.Message> messages, List<ToolCallback> callbacks) {
        DefaultToolCallingChatOptions options = (DefaultToolCallingChatOptions) DefaultToolCallingChatOptions.builder()
                .model("deepseek-chat")
                .internalToolExecutionEnabled(true)
                .toolCallbacks(callbacks)
                .toolContext(Map.of(
                        AicrediFluxGatewayChatModel.CTX_USER_ID, 7,
                        AicrediFluxGatewayChatModel.CTX_GROUP, "default",
                        AicrediFluxGatewayChatModel.CTX_REQUEST_ID, "req-1",
                        AicrediFluxGatewayChatModel.CTX_SESSION_NO, "AS001",
                        AicrediFluxGatewayChatModel.CTX_RUN_NO, "AR001"))
                .build();
        return new Prompt(messages, options);
    }

    private ToolCallback walletTool() {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("walletBalance")
                        .description("查询当前用户 AI Credit 余额")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return "{\"balance\":128000}";
            }
        };
    }
}
