package aicrediflux.token.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import aicrediflux.token.dispatch.ModelDispatchService;
import aicrediflux.token.dispatch.RelayInfoBuilder;
import aicrediflux.token.pojo.dto.Usage;
import aicrediflux.token.pojo.entity.Channel;
import aicrediflux.token.relay.common.RelayInfo;
import aicrediflux.token.service.ChannelService;
import aicrediflux.token.service.QuotaService;

class DefaultInternalChatServiceTest {
    @Test
    void internalAgentChatSelectsChannelAndCarriesDispatchMetadata() throws Exception {
        ModelDispatchService dispatchService = org.mockito.Mockito.mock(ModelDispatchService.class);
        RelayInfoBuilder relayInfoBuilder = new RelayInfoBuilder();
        ChannelService channelService = org.mockito.Mockito.mock(ChannelService.class);
        QuotaService quotaService = org.mockito.Mockito.mock(QuotaService.class);
        Channel channel = new Channel();
        channel.setId(3);
        channel.setName("mock-openai");
        channel.setType(1);
        channel.setKey("sk-test");
        channel.setBaseUrl("http://upstream.local");
        when(channelService.getRandomSatisfiedChannel(eq("default"), eq("qwen-plus"), eq(0), any()))
                .thenReturn(channel);
        when(quotaService.calculateTextQuotaWithCache(any(RelayInfo.class), any(Usage.class))).thenReturn(20);
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.setStatus(200);
            response.getWriter().write("""
                    {"choices":[{"message":{"content":"你好，我是 Copilot。"}}],"usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}}
                    """);
            return null;
        }).when(dispatchService).dispatchRelay(any(), any(), any(RelayInfo.class), eq("openai"));

        DefaultInternalChatService service = new DefaultInternalChatService(
                dispatchService, relayInfoBuilder, channelService, quotaService);
        InternalChatResult result = service.chat(new InternalChatRequest(7, "default", "qwen-plus",
                List.of(new InternalChatMessage("user", "你好")), "req-agent-1", "AS001", "AR001", false));

        ArgumentCaptor<RelayInfo> infoCaptor = ArgumentCaptor.forClass(RelayInfo.class);
        verify(dispatchService).dispatchRelay(any(), any(), infoCaptor.capture(), eq("openai"));
        RelayInfo info = infoCaptor.getValue();
        assertThat(info.getUserId()).isEqualTo(7);
        assertThat(info.getChannelId()).isEqualTo(3);
        assertThat(info.getOriginModelName()).isEqualTo("qwen-plus");
        assertThat(info.getExtraData()).containsEntry("dispatch_source", "AGENT");
        assertThat(info.getExtraData()).containsEntry("dispatch_session_id", "AS001");
        assertThat(info.getExtraData()).containsEntry("dispatch_run_id", "AR001");
        assertThat(result.content()).isEqualTo("你好，我是 Copilot。");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.completionTokens()).isEqualTo(8);
        assertThat(result.quota()).isEqualTo(20);
    }
}
