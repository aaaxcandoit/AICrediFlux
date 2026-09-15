package aicrediflux.token.dispatch;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import aicrediflux.token.constant.CommonConstants;
import aicrediflux.token.constant.ContextKeyConstants;
import aicrediflux.token.mapper.LogMapper;
import aicrediflux.token.pojo.dto.ChannelInfoDTO;
import aicrediflux.token.pojo.dto.ErrorCode;
import aicrediflux.token.pojo.dto.RelayException;
import aicrediflux.token.pojo.dto.TokenCountMeta;
import aicrediflux.token.pojo.entity.Channel;
import aicrediflux.token.pojo.entity.Log;
import aicrediflux.token.relay.common.RelayInfo;
import aicrediflux.token.relay.constant.RelayModeEnum;
import aicrediflux.token.relay.handler.AudioHandler;
import aicrediflux.token.relay.handler.ClaudeHandler;
import aicrediflux.token.relay.handler.CompatibleHandler;
import aicrediflux.token.relay.handler.EmbeddingHandler;
import aicrediflux.token.relay.handler.GeminiHandler;
import aicrediflux.token.relay.handler.ImageHandler;
import aicrediflux.token.relay.handler.RerankHandler;
import aicrediflux.token.relay.handler.ResponsesHandler;
import aicrediflux.token.relay.helper.PriceHelper;
import aicrediflux.token.service.BillingService;
import aicrediflux.token.service.ChannelSelectService;
import aicrediflux.token.service.ChannelService;
import aicrediflux.token.service.PerfMetricsService;
import aicrediflux.token.service.RetryParam;
import aicrediflux.token.service.TokenCounterService;
import aicrediflux.token.spi.ChannelHealthHandler;
import aicrediflux.token.spi.ChannelSelector;
import aicrediflux.token.spi.RelayRequestInterceptor;
import aicrediflux.token.spi.RelayRetryStrategy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ModelDispatchService {

    private final CompatibleHandler compatibleHandler;
    private final ClaudeHandler claudeHandler;
    private final GeminiHandler geminiHandler;
    private final EmbeddingHandler embeddingHandler;
    private final RerankHandler rerankHandler;
    private final AudioHandler audioHandler;
    private final ImageHandler imageHandler;
    private final ResponsesHandler responsesHandler;
    private final ChannelService channelService;
    private final LogMapper logMapper;
    private final PerfMetricsService perfMetricsService;
    private final RelayRetryStrategy relayRetryStrategy;
    private final ChannelHealthHandler channelHealthHandler;
    private final RelayRequestInterceptor relayRequestInterceptor;
    private final BillingService billingService;
    private final TokenCounterService tokenCounterService;
    private ChannelSelector channelSelector;

    public ModelDispatchService(CompatibleHandler compatibleHandler,
                                ClaudeHandler claudeHandler,
                                GeminiHandler geminiHandler,
                                EmbeddingHandler embeddingHandler,
                                RerankHandler rerankHandler,
                                AudioHandler audioHandler,
                                ImageHandler imageHandler,
                                ResponsesHandler responsesHandler,
                                ChannelService channelService,
                                LogMapper logMapper,
                                PerfMetricsService perfMetricsService,
                                RelayRetryStrategy relayRetryStrategy,
                                ChannelHealthHandler channelHealthHandler,
                                RelayRequestInterceptor relayRequestInterceptor,
                                BillingService billingService,
                                TokenCounterService tokenCounterService) {
        this.compatibleHandler = compatibleHandler;
        this.claudeHandler = claudeHandler;
        this.geminiHandler = geminiHandler;
        this.embeddingHandler = embeddingHandler;
        this.rerankHandler = rerankHandler;
        this.audioHandler = audioHandler;
        this.imageHandler = imageHandler;
        this.responsesHandler = responsesHandler;
        this.channelService = channelService;
        this.logMapper = logMapper;
        this.perfMetricsService = perfMetricsService;
        this.relayRetryStrategy = relayRetryStrategy;
        this.channelHealthHandler = channelHealthHandler;
        this.relayRequestInterceptor = relayRequestInterceptor;
        this.billingService = billingService;
        this.tokenCounterService = tokenCounterService;
    }

    @Autowired(required = false)
    public void setChannelSelector(ChannelSelector channelSelector) {
        this.channelSelector = channelSelector;
    }

    public void dispatchRelay(HttpServletRequest req, HttpServletResponse resp, RelayInfo info, String relayFormat) {
        info.setRelayFormat(relayFormat);
        RetryParam retryParam = new RetryParam(req, info.getTokenGroup(), info.getOriginModelName());
        info.setRetryIndex(0);
        info.setLastError(null);

        RelayException lastError = null;

        TokenCountMeta pricingMeta = tokenCounterService.fastTokenCountMetaForPricing(info.getRequest());
        info.setEstimatePromptTokens(pricingMeta.getEstimatedPromptTokens());
        PriceHelper.modelPriceHelper(info, 0, pricingMeta.getMaxTokens());
        if (!info.getPriceData().isFreeModel()) {
            String preConsumeErr = billingService.preConsumeBilling(info, info.getPriceData().getQuotaToPreConsume());
            if (preConsumeErr != null) {
                RelayException ex = new RelayException(preConsumeErr, ErrorCode.INSUFFICIENT_USER_QUOTA);
                ex.setStatusCode(429);
                writeApiError(resp, ex, relayFormat);
                return;
            }
        }

        try {
            for (; retryParam.getRetry() <= CommonConstants.retryTimes; retryParam.increaseRetry()) {
                info.setRetryIndex(retryParam.getRetry());

                if (retryParam.getRetry() > 0) {
                    if (lastError != null && isKeyLevelError(lastError) && tryNextKeyOnSameChannel(req, info)) {
                        log.debug("号池切换：channel={} 换 key 重试，keyIndex={}",
                                info.getChannelId(), info.getChannelMultiKeyIndex());
                    } else {
                        Channel channel = selectChannelForRetry(info, retryParam);
                        if (channel == null) {
                            log.warn("重试无可用备用渠道，保留首次上游错误：group={}, model={}, retry={}, firstError={}",
                                    info.getTokenGroup(), info.getOriginModelName(), retryParam.getRetry(),
                                    lastError == null ? null : truncateMsg(lastError.getMessage()));
                            if (lastError == null) {
                                lastError = new RelayException(
                                        "no available channel for retry (group=" + info.getTokenGroup()
                                                + ", model=" + info.getOriginModelName() + ")",
                                        ErrorCode.GET_CHANNEL_FAILED);
                                lastError.setStatusCode(503);
                            }
                            break;
                        }
                        setupChannelContextOnRetry(req, channel, info);
                        addUsedChannel(req, channel.getId());
                    }
                    parseRequestBody(req, info);
                } else {
                    Integer channelId = (Integer) req.getAttribute(ContextKeyConstants.CHANNEL_ID);
                    if (channelId != null) {
                        addUsedChannel(req, channelId);
                    }
                }

                try {
                    if (relayRequestInterceptor != null) {
                        relayRequestInterceptor.preRequest(info);
                    }

                    switch (relayFormat) {
                        case "claude":
                            claudeHandler.claudeHelper(req, resp, info);
                            break;
                        case "gemini":
                            geminiHandler.geminiHelper(req, resp, info);
                            break;
                        default:
                            dispatchByMode(req, resp, info);
                    }
                    info.setLastError(null);
                    if (relayRequestInterceptor != null) {
                        relayRequestInterceptor.postResponse(info, null);
                    }
                    recordPerfSample(info, true, info.getPerfOutputTokens());
                    return;
                } catch (RelayException e) {
                    lastError = e;
                    info.setLastError(e);

                    if (isClientDisconnect(e)) {
                        log.warn("客户端断开连接：channel={}, model={}",
                                info.getChannelId(), info.getOriginModelName());
                        break;
                    }

                    processChannelError(req, info, e);
                    if (relayRequestInterceptor != null) {
                        relayRequestInterceptor.postResponse(info, e);
                    }
                    if (!shouldRetry(req, info, e, CommonConstants.retryTimes - retryParam.getRetry())) {
                        break;
                    }
                }
            }

            if (lastError != null && isClientDisconnect(lastError)) {
                return;
            }

            logRetrySummary(req);
            if (lastError != null && info != null) {
                recordPerfSample(info, false, 0);
            }
            writeApiError(resp, lastError, relayFormat);
        } finally {
            if (info.getBilling() != null) {
                info.getBilling().refund();
            }
        }
    }

    private Channel selectChannelForRetry(RelayInfo info, RetryParam retryParam) {
        Object[] result = ChannelSelectService.cacheGetRandomSatisfiedChannel(retryParam, channelService, channelSelector);
        Channel channel = (Channel) result[0];
        String selectGroup = (String) result[1];

        if (channel == null) {
            log.warn("重试选渠道失败：group={}, model={}, retry={}",
                    retryParam.getTokenGroup(), retryParam.getModelName(), retryParam.getRetry());
        } else {
            log.debug("重试选中渠道：channel={}(#{}) group={}", channel.getName(), channel.getId(), selectGroup);
        }
        return channel;
    }

    private void setupChannelContextOnRetry(HttpServletRequest req, Channel channel, RelayInfo info) {
        setAttr(req, ContextKeyConstants.CHANNEL_ID, channel.getId());
        setAttr(req, ContextKeyConstants.CHANNEL_NAME, channel.getName());
        setAttr(req, ContextKeyConstants.CHANNEL_TYPE, channel.getType());
        setAttr(req, ContextKeyConstants.CHANNEL_BASE_URL, channel.getBaseUrl());

        ChannelInfoDTO channelInfo = parseChannelInfo(channel);
        int[] keyIndex = new int[1];
        String key = selectKey(channel, channelInfo, keyIndex);
        setAttr(req, ContextKeyConstants.CHANNEL_KEY, key);
        setAttr(req, ContextKeyConstants.CHANNEL_IS_MULTI_KEY, channelInfo != null && channelInfo.isMultiKey());
        if (channelInfo != null && channelInfo.isMultiKey()) {
            req.setAttribute("channel_multi_key_index", keyIndex[0]);
            info.setChannelIsMultiKey(true);
            info.setChannelMultiKeyIndex(keyIndex[0]);
        } else {
            info.setChannelIsMultiKey(false);
            info.setChannelMultiKeyIndex(0);
        }

        setAttr(req, ContextKeyConstants.CHANNEL_ORGANIZATION, channel.getOpenaiOrganization());
        setAttr(req, ContextKeyConstants.CHANNEL_SETTING, channel.getSetting());
        setAttr(req, ContextKeyConstants.CHANNEL_OTHER_SETTING, channel.getOtherInfo());
        setAttr(req, ContextKeyConstants.CHANNEL_MODEL_MAPPING, channel.getModelMapping());
        setAttr(req, ContextKeyConstants.CHANNEL_STATUS_CODE_MAPPING, channel.getStatusCodeMapping());
        setAttr(req, ContextKeyConstants.CHANNEL_AUTO_BAN, channel.getAutoBan());

        info.setChannelId(channel.getId());
        info.setChannelType(channel.getType());
        info.setChannelBaseUrl(channel.getBaseUrl());
        info.setApiKey(key);
    }

    private boolean isKeyLevelError(RelayException error) {
        if (error == null) {
            return false;
        }
        int code = error.getStatusCode();
        return code == 401 || code == 429;
    }

    @SuppressWarnings("unchecked")
    private boolean tryNextKeyOnSameChannel(HttpServletRequest req, RelayInfo info) {
        if (!info.isChannelIsMultiKey()) {
            return false;
        }
        Integer channelId = info.getChannelId();
        if (channelId == null) {
            return false;
        }
        Channel channel = channelService.getById(channelId);
        if (channel == null) {
            return false;
        }

        List<String> keys = parseKeyList(channel.getKey());
        if (keys.size() <= 1) {
            return false;
        }

        List<Integer> triedKeyIndices = (List<Integer>) req.getAttribute("tried_key_indices");
        if (triedKeyIndices == null) {
            triedKeyIndices = new ArrayList<>();
            if (info.getChannelMultiKeyIndex() >= 0) {
                triedKeyIndices.add(info.getChannelMultiKeyIndex());
            }
        }

        ChannelInfoDTO channelInfo = parseChannelInfo(channel);
        Map<Integer, Integer> statusList = channelInfo != null ? channelInfo.getMultiKeyStatusList() : null;
        int currentIdx = info.getChannelMultiKeyIndex();
        for (int i = 1; i <= keys.size(); i++) {
            int idx = (currentIdx + i) % keys.size();
            if (triedKeyIndices.contains(idx)) {
                continue;
            }
            if (statusList != null) {
                Integer status = statusList.get(idx);
                if (status != null && status != CommonConstants.CHANNEL_STATUS_ENABLED) {
                    continue;
                }
            }
            triedKeyIndices.add(idx);
            req.setAttribute("tried_key_indices", triedKeyIndices);
            info.setApiKey(keys.get(idx));
            info.setChannelMultiKeyIndex(idx);
            setAttr(req, ContextKeyConstants.CHANNEL_KEY, keys.get(idx));
            req.setAttribute("channel_multi_key_index", idx);
            return true;
        }
        return false;
    }

    private ChannelInfoDTO parseChannelInfo(Channel channel) {
        String json = channel.getChannelInfo();
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return Convert.toJavaBean(json, ChannelInfoDTO.class);
        } catch (Exception e) {
            log.debug("Failed to parse channel_info for channel {}: {}", channel.getId(), e.getMessage());
            return null;
        }
    }

    private List<String> parseKeyList(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return List.of();
        }
        String key = rawKey.trim();
        if (key.startsWith("[")) {
            try {
                String[] arr = Convert.toJavaBean(key, String[].class);
                return Arrays.asList(arr);
            } catch (Exception e) {
                log.debug("multi-key JSON 解析失败: {}", e.getMessage());
            }
        }
        if (key.contains("\n")) {
            return Arrays.stream(key.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return List.of(key);
    }

    private String selectKey(Channel channel, ChannelInfoDTO channelInfo, int[] outKeyIndex) {
        outKeyIndex[0] = 0;
        if (channel.getKey() == null || channel.getKey().isEmpty()) {
            return "";
        }
        if (channelInfo == null || !channelInfo.isMultiKey()) {
            String key = channel.getKey().trim();
            if (key.startsWith("[")) {
                try {
                    String[] arr = Convert.toJavaBean(key, String[].class);
                    return arr.length > 0 ? arr[0] : "";
                } catch (Exception e) {
                    log.debug("single-key JSON 解析失败: {}", e.getMessage());
                }
            }
            int newlineIdx = key.indexOf('\n');
            return newlineIdx > 0 ? key.substring(0, newlineIdx).trim() : key;
        }

        List<String> keys = parseKeyList(channel.getKey());
        if (keys.isEmpty()) {
            return "";
        }
        int size = keys.size();
        int startIndex = channelInfo.getMultiKeyPollingIndex() % size;
        Map<Integer, Integer> statusList = channelInfo.getMultiKeyStatusList();
        for (int i = 0; i < size; i++) {
            int idx = (startIndex + i) % size;
            if (statusList != null) {
                Integer status = statusList.get(idx);
                if (status != null && status != CommonConstants.CHANNEL_STATUS_ENABLED) {
                    continue;
                }
            }
            outKeyIndex[0] = idx;
            return keys.get(idx);
        }
        outKeyIndex[0] = 0;
        return keys.get(0);
    }

    @SuppressWarnings("unchecked")
    private void addUsedChannel(HttpServletRequest req, int channelId) {
        List<Integer> usedChannels = (List<Integer>) req.getAttribute("use_channel");
        if (usedChannels == null) {
            usedChannels = new ArrayList<>();
        }
        if (!usedChannels.contains(channelId)) {
            usedChannels.add(channelId);
        }
        req.setAttribute("use_channel", usedChannels);
    }

    private boolean shouldRetry(HttpServletRequest req, RelayInfo info, RelayException error, int remainingRetries) {
        if (error == null) {
            return false;
        }
        if (aicrediflux.token.service.ChannelAffinityService.shouldSkipRetryAfterAffinityFailure(req)) {
            return false;
        }
        if (remainingRetries <= 0) {
            return false;
        }
        if (req.getAttribute("specific_channel_id") != null) {
            return false;
        }
        if (relayRetryStrategy != null) {
            return relayRetryStrategy.shouldRetry(info, error, remainingRetries);
        }
        return false;
    }

    private void processChannelError(HttpServletRequest req, RelayInfo info, RelayException error) {
        log.error("中继路径渠道错误：statusCode={}, errorCode={}, message={}",
                error.getStatusCode(), error.getErrorCode(), truncateMsg(error.getMessage()));

        if (channelHealthHandler != null) {
            channelHealthHandler.onChannelError(info, error);
        }
        if (CommonConstants.isMasterNode && isRecordErrorLog(error)) {
            recordErrorLog(req, error);
        }
    }

    private boolean isRecordErrorLog(RelayException error) {
        return error != null && error.isRecordErrorLog();
    }

    private void recordErrorLog(HttpServletRequest req, RelayException error) {
        try {
            Log log = new Log();
            log.setUserId(toInt(req.getAttribute("id")));
            log.setChannelId(toInt(req.getAttribute(ContextKeyConstants.CHANNEL_ID)));
            log.setModelName((String) req.getAttribute("original_model"));
            log.setTokenName((String) req.getAttribute("token_name"));
            log.setContent(error.maskSensitiveErrorWithStatusCode());
            log.setTokenId(toInt(req.getAttribute("token_id")));
            log.setUsername((String) req.getAttribute("username"));
            log.setGroup((String) req.getAttribute("group"));

            Long startTime = (Long) req.getAttribute("request_start_time");
            log.setUseTime(startTime != null ? (int) ((System.currentTimeMillis() - startTime) / 1000) : 0);
            log.setIsStream(Boolean.TRUE.equals(req.getAttribute("is_stream")));

            Object requestId = req.getAttribute("request_id");
            if (requestId instanceof String sid) {
                log.setRequestId(sid);
            }

            log.setCreatedAt(Instant.now().getEpochSecond());
            log.setType(5);
            Map<String, Object> other = new LinkedHashMap<>();
            other.put("error_type", error.getErrorType());
            other.put("error_code", error.getErrorCode());
            other.put("status_code", error.getStatusCode());
            other.put("channel_id", req.getAttribute(ContextKeyConstants.CHANNEL_ID));
            other.put("channel_type", req.getAttribute(ContextKeyConstants.CHANNEL_TYPE));
            other.put("request_path", req.getRequestURI());
            other.put("dispatch_source", req.getAttribute("dispatch_source"));
            other.put("dispatch_session_id", req.getAttribute("dispatch_session_id"));
            other.put("dispatch_run_id", req.getAttribute("dispatch_run_id"));

            @SuppressWarnings("unchecked")
            List<Integer> useChannel = (List<Integer>) req.getAttribute("use_channel");
            Map<String, Object> adminInfo = new LinkedHashMap<>();
            adminInfo.put("use_channel", useChannel);
            other.put("admin_info", adminInfo);

            log.setOther(Convert.toJSONString(other));
            logMapper.insert(log);
        } catch (Exception e) {
            log.error("记录错误日志失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void logRetrySummary(HttpServletRequest req) {
        List<Integer> useChannel = (List<Integer>) req.getAttribute("use_channel");
        if (useChannel != null && useChannel.size() > 1) {
            StringBuilder sb = new StringBuilder("重试：");
            for (int i = 0; i < useChannel.size(); i++) {
                if (i > 0) {
                    sb.append("->");
                }
                sb.append(useChannel.get(i));
            }
            log.info(sb.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void parseRequestBody(HttpServletRequest req, RelayInfo info) {
        String contentType = req.getContentType();
        if (contentType == null || !contentType.contains("application/json")) {
            return;
        }
        try {
            Map<String, Object> body = (Map<String, Object>) req.getAttribute(ContextKeyConstants.PARSED_REQUEST_BODY);
            if (body == null) {
                body = Convert.toJSONObject(
                        new String(req.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            info.setRequest(body);
            String model = (String) body.get("model");
            if (model == null) {
                String path = req.getRequestURI();
                if (path.contains("/models/")) {
                    String[] parts = path.split("/models/");
                    if (parts.length > 1) {
                        model = parts[1].replaceAll(":.*", "");
                    }
                }
            }
            if (info.getOriginModelName() == null || info.getOriginModelName().isEmpty()) {
                info.setOriginModelName(model);
            }
            info.setUpstreamModelName((String) req.getAttribute("upstream_model_name"));
            if (info.getUpstreamModelName() == null || info.getUpstreamModelName().isEmpty()) {
                info.setUpstreamModelName(model);
            }
        } catch (Exception e) {
            log.debug("Failed to parse request body for model extraction: {}", e.getMessage());
        }
    }

    private void setAttr(HttpServletRequest req, String key, Object value) {
        if (value != null) {
            req.setAttribute(key, value);
        }
    }

    private static String truncateMsg(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    private void dispatchByMode(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        int mode = info.getRelayMode();
        if (mode == RelayModeEnum.IMAGES_GENERATIONS || mode == RelayModeEnum.IMAGES_EDITS) {
            imageHandler.imageHelper(req, resp, info);
        } else if (mode == RelayModeEnum.AUDIO_SPEECH
                || mode == RelayModeEnum.AUDIO_TRANSCRIPTION
                || mode == RelayModeEnum.AUDIO_TRANSLATION) {
            audioHandler.audioHelper(req, resp, info);
        } else if (mode == RelayModeEnum.RERANK) {
            rerankHandler.rerankHelper(req, resp, info);
        } else if (mode == RelayModeEnum.EMBEDDINGS) {
            embeddingHandler.embeddingHelper(req, resp, info);
        } else if (mode == RelayModeEnum.RESPONSES || mode == RelayModeEnum.RESPONSES_COMPACT) {
            responsesHandler.responsesHelper(req, resp, info);
        } else {
            compatibleHandler.textHelper(req, resp, info);
        }
    }

    private void recordPerfSample(RelayInfo info, boolean success, long outputTokens) {
        try {
            long startMs = info.getStartTime() != null
                    ? info.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
            long firstRespMs = info.getFirstResponseTime() != null
                    ? info.getFirstResponseTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
            perfMetricsService.recordRelaySample(
                    info.getOriginModelName(),
                    info.getUsingGroup() != null ? info.getUsingGroup() : "default",
                    info.isStream(),
                    info.hasSendResponse(),
                    startMs,
                    firstRespMs,
                    success,
                    outputTokens);
        } catch (Exception ignore) {
            // 性能采样不影响主流程
        }
    }

    private void writeApiError(HttpServletResponse resp, RelayException error, String relayFormat) {
        try {
            if (error == null) {
                error = new RelayException("unknown relay error", ErrorCode.DO_REQUEST_FAILED);
                error.setStatusCode(500);
            }
            resp.setStatus(error.getStatusCode() != 0 ? error.getStatusCode() : 500);
            resp.setContentType("application/json;charset=UTF-8");

            Map<String, Object> errorBody = new LinkedHashMap<>();
            if ("claude".equals(relayFormat)) {
                Map<String, Object> claudeErr = new LinkedHashMap<>();
                claudeErr.put("type", "error");
                claudeErr.put("error", error.toClaudeError());
                resp.getWriter().write(Convert.toJSONString(claudeErr));
            } else {
                errorBody.put("error", error.toOpenAIError());
                resp.getWriter().write(Convert.toJSONString(errorBody));
            }
        } catch (Exception e) {
            log.error("Failed to write API error response", e);
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private boolean isClientDisconnect(RelayException error) {
        if (error == null) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("broken pipe") || lower.contains("response not usable")
                        || lower.contains("clientabort")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}

