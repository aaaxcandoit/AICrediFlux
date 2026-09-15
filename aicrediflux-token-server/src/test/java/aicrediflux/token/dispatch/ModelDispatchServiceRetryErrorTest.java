package aicrediflux.token.dispatch;

import aicrediflux.token.config.ratio.ModelRatioConfig;
import aicrediflux.token.constant.CommonConstants;
import aicrediflux.token.constant.ContextKeyConstants;
import aicrediflux.token.mapper.LogMapper;
import aicrediflux.token.pojo.dto.EmbeddingDTO;
import aicrediflux.token.pojo.dto.ErrorCode;
import aicrediflux.token.pojo.dto.RelayException;
import aicrediflux.token.pojo.dto.TokenCountMeta;
import aicrediflux.token.relay.common.RelayInfo;
import aicrediflux.token.relay.constant.RelayModeEnum;
import aicrediflux.token.relay.handler.EmbeddingHandler;
import aicrediflux.token.service.BillingService;
import aicrediflux.token.service.ChannelService;
import aicrediflux.token.service.PerfMetricsService;
import aicrediflux.token.service.RetryParam;
import aicrediflux.token.service.TokenCounterService;
import aicrediflux.token.spi.RelayRetryStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ModelDispatchService retry 错误保留")
class ModelDispatchServiceRetryErrorTest {

    private static final String MODEL = "Qwen/Qwen3-Embedding-0.6B";
    private final int originalRetryTimes = CommonConstants.retryTimes;

    @AfterEach
    void tearDown() {
        ModelRatioConfig.removeModelRatio(MODEL);
        CommonConstants.retryTimes = originalRetryTimes;
    }

    @Test
    @DisplayName("首次上游失败后没有可重试渠道时，应保留首次真实错误")
    void preservesOriginalUpstreamErrorWhenRetryHasNoChannel() throws Exception {
        CommonConstants.retryTimes = 1;
        EmbeddingHandler embeddingHandler = mock(EmbeddingHandler.class);
        ChannelService channelService = mock(ChannelService.class);
        LogMapper logMapper = mock(LogMapper.class);
        PerfMetricsService perfMetricsService = mock(PerfMetricsService.class);
        RelayRetryStrategy relayRetryStrategy = mock(RelayRetryStrategy.class);
        BillingService billingService = mock(BillingService.class);
        TokenCounterService tokenCounterService = mock(TokenCounterService.class);

        RelayException upstreamError = new RelayException("SiliconFlow upstream rejected embedding request",
                ErrorCode.BAD_RESPONSE_STATUS_CODE);
        upstreamError.setStatusCode(400);
        doThrow(upstreamError).when(embeddingHandler)
                .embeddingHelper(any(MockHttpServletRequest.class), any(MockHttpServletResponse.class), any(RelayInfo.class));
        when(relayRetryStrategy.shouldRetry(any(RelayInfo.class), eq(upstreamError), anyInt())).thenReturn(true);
        when(channelService.getRandomSatisfiedChannel(eq("default"), eq(MODEL), eq(1), any(Set.class)))
                .thenReturn(null);
        when(tokenCounterService.fastTokenCountMetaForPricing(any()))
                .thenReturn(new TokenCountMeta(null, "", 0, 0, 0, null, 0, 1, 1.0));
        when(billingService.preConsumeBilling(any(RelayInfo.class), any(Integer.class))).thenReturn(null);
        ModelRatioConfig.putModelRatio(MODEL, 1.0);

        ModelDispatchService service = new ModelDispatchService(null, null, null, embeddingHandler,
                null, null, null, null, channelService, logMapper, perfMetricsService,
                relayRetryStrategy, null, null, billingService, tokenCounterService);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/embeddings");
        request.setAttribute(ContextKeyConstants.CHANNEL_ID, 3);
        request.setAttribute(ContextKeyConstants.CHANNEL_TYPE, 1);
        request.setAttribute(ContextKeyConstants.PARSED_REQUEST_BODY,
                java.util.Map.of("model", MODEL, "input", List.of("hello")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RelayInfo info = new RelayInfo()
                .setRelayMode(RelayModeEnum.EMBEDDINGS)
                .setOriginModelName(MODEL)
                .setTokenGroup("default")
                .setUsingGroup("default")
                .setUserGroup("default")
                .setChannelId(3)
                .setChannelType(1)
                .setStartTime(LocalDateTime.now())
                .setRequest(new EmbeddingDTO(MODEL, List.of("hello"), null, null,
                        null, null, null, null, null, null));

        service.dispatchRelay(request, response, info, "openai");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("SiliconFlow upstream rejected embedding request");
        assertThat(response.getContentAsString()).doesNotContain("no available channel for retry");
        verify(channelService).getRandomSatisfiedChannel(eq("default"), eq(MODEL), eq(1), any(Set.class));
    }
}
