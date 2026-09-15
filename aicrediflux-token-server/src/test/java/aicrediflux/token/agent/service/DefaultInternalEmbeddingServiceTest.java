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

import aicrediflux.token.dispatch.DispatchSource;
import aicrediflux.token.dispatch.ModelDispatchService;
import aicrediflux.token.dispatch.RelayInfoBuilder;
import aicrediflux.token.pojo.dto.EmbeddingDTO;
import aicrediflux.token.pojo.dto.Usage;
import aicrediflux.token.pojo.entity.Channel;
import aicrediflux.token.relay.common.RelayInfo;
import aicrediflux.token.relay.constant.RelayModeEnum;
import aicrediflux.token.service.ChannelService;
import aicrediflux.token.service.QuotaService;

class DefaultInternalEmbeddingServiceTest {
    @Test
    void internalRagEmbeddingSelectsChannelAndCarriesDispatchMetadata() throws Exception {
        ModelDispatchService dispatchService = org.mockito.Mockito.mock(ModelDispatchService.class);
        RelayInfoBuilder relayInfoBuilder = new RelayInfoBuilder();
        ChannelService channelService = org.mockito.Mockito.mock(ChannelService.class);
        QuotaService quotaService = org.mockito.Mockito.mock(QuotaService.class);
        Channel channel = new Channel();
        channel.setId(8);
        channel.setName("SiliconFlow");
        channel.setType(1);
        channel.setKey("sk-test");
        channel.setBaseUrl("https://api.siliconflow.cn/v1");
        when(channelService.getRandomSatisfiedChannel(eq("default"), eq("Qwen/Qwen3-Embedding-0.6B"), eq(0), any()))
                .thenReturn(channel);
        when(quotaService.calculateTextQuotaWithCache(any(RelayInfo.class), any(Usage.class))).thenReturn(3);
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.setStatus(200);
            response.getWriter().write("""
                    {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]},{"object":"embedding","index":1,"embedding":[0.4,0.5,0.6]}],"model":"Qwen/Qwen3-Embedding-0.6B","usage":{"prompt_tokens":3,"total_tokens":3}}
                    """);
            return null;
        }).when(dispatchService).dispatchRelay(any(), any(), any(RelayInfo.class), eq("openai"));

        DefaultInternalEmbeddingService service = new DefaultInternalEmbeddingService(
                dispatchService, relayInfoBuilder, channelService, quotaService);
        InternalEmbeddingResult result = service.embed(new InternalEmbeddingRequest(7, "default",
                "Qwen/Qwen3-Embedding-0.6B", List.of("hello", "rag"), "req-rag-1", DispatchSource.RAG_INDEX));

        ArgumentCaptor<RelayInfo> infoCaptor = ArgumentCaptor.forClass(RelayInfo.class);
        verify(dispatchService).dispatchRelay(any(), any(), infoCaptor.capture(), eq("openai"));
        RelayInfo info = infoCaptor.getValue();
        assertThat(info.getRelayMode()).isEqualTo(RelayModeEnum.EMBEDDINGS);
        assertThat(info.getUserId()).isEqualTo(7);
        assertThat(info.getChannelId()).isEqualTo(8);
        assertThat(info.getOriginModelName()).isEqualTo("Qwen/Qwen3-Embedding-0.6B");
        assertThat(info.getExtraData()).containsEntry("dispatch_source", "RAG_INDEX");
        assertThat(info.getRequest()).isInstanceOf(EmbeddingDTO.class);
        EmbeddingDTO request = (EmbeddingDTO) info.getRequest();
        assertThat(request.getInput()).isEqualTo(List.of("hello", "rag"));
        assertThat(result.embeddings()).hasSize(2);
        assertThat(result.embeddings().get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.promptTokens()).isEqualTo(3);
        assertThat(result.quota()).isEqualTo(3);
        assertThat(result.channelId()).isEqualTo(8);
    }
}