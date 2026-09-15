package aicrediflux.token.dispatch;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import aicrediflux.token.constant.ContextKeyConstants;
import aicrediflux.token.pojo.dto.ChannelOtherSettingsDTO;
import aicrediflux.token.pojo.dto.ChannelSettingsDTO;
import aicrediflux.token.relay.common.RelayInfo;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class RelayInfoBuilder {

    public RelayInfo buildApi(HttpServletRequest req, DispatchContext context) {
        RelayInfo info = buildCommon(req, context);
        info.setTokenId(context.tokenId() != null ? context.tokenId() : toInt(req.getAttribute("token_id")));
        info.setTokenKey((String) req.getAttribute("token_key"));
        info.setTokenGroup(resolveString(req.getAttribute("token_group"), context.group()));
        info.setTokenUnlimited(Boolean.TRUE.equals(req.getAttribute("token_unlimited")));
        info.setRequestURLPath(req.getRequestURI());
        return info;
    }

    public RelayInfo buildPlayground(HttpServletRequest req, DispatchContext context) {
        RelayInfo info = buildCommon(req, context);
        String group = resolveString(context.group(), resolveString(
                req.getAttribute(ContextKeyConstants.USING_GROUP), req.getAttribute("group")));
        info.setUserId(context.userId());
        info.setTokenId(0);
        info.setTokenGroup(group);
        info.setTokenKey("playground-" + (group != null ? group : "default"));
        info.setTokenUnlimited(true);
        info.setPlayground(true);
        String requestPath = req.getRequestURI();
        if (requestPath.startsWith("/pg")) {
            requestPath = "/v1" + requestPath.substring(3);
        }
        info.setRequestURLPath(requestPath);
        return info;
    }

    @SuppressWarnings("unchecked")
    private RelayInfo buildCommon(HttpServletRequest req, DispatchContext context) {
        RelayInfo info = new RelayInfo();
        info.setStartTime(LocalDateTime.now());
        info.setRequestId(resolveRequestId(req, context));

        Integer channelId = (Integer) req.getAttribute(ContextKeyConstants.CHANNEL_ID);
        Integer channelType = (Integer) req.getAttribute(ContextKeyConstants.CHANNEL_TYPE);
        if (channelId != null) {
            info.setChannelId(channelId);
        }
        if (channelType != null) {
            info.setChannelType(channelType);
        }
        info.setChannelBaseUrl(normalizeBaseUrl((String) req.getAttribute(ContextKeyConstants.CHANNEL_BASE_URL)));
        info.setApiKey((String) req.getAttribute(ContextKeyConstants.CHANNEL_KEY));
        info.setOrganization((String) req.getAttribute(ContextKeyConstants.CHANNEL_ORGANIZATION));
        info.setApiType(channelType != null ? channelType : 0);

        Object settingObj = req.getAttribute(ContextKeyConstants.CHANNEL_SETTING);
        if (settingObj instanceof ChannelSettingsDTO cs) {
            info.setChannelSetting(cs);
        }
        Object otherSettingObj = req.getAttribute(ContextKeyConstants.CHANNEL_OTHER_SETTING);
        if (otherSettingObj instanceof ChannelOtherSettingsDTO cos) {
            info.setChannelOtherSettings(cos);
        }

        String headerOverrideJson = (String) req.getAttribute(ContextKeyConstants.CHANNEL_HEADER_OVERRIDE);
        if (headerOverrideJson != null && !headerOverrideJson.isEmpty()) {
            try {
                Map<String, Object> headerOverride = Convert.toJSONObject(headerOverrideJson);
                info.setHeadersOverride(headerOverride);
            } catch (Exception e) {
                log.debug("headerOverride JSON 解析失败: {}", e.getMessage());
            }
        }
        String paramOverrideJson = (String) req.getAttribute(ContextKeyConstants.CHANNEL_PARAM_OVERRIDE);
        if (paramOverrideJson != null && !paramOverrideJson.isEmpty()) {
            try {
                Map<String, Object> paramOverride = Convert.toJSONObject(paramOverrideJson);
                info.setParamOverride(paramOverride);
            } catch (Exception e) {
                log.debug("paramOverride JSON 解析失败: {}", e.getMessage());
            }
        }

        info.setModelMapped(parseModelMapping(req));
        info.setUserId(context.userId() > 0 ? context.userId() : toInt(req.getAttribute("id")));
        info.setUsingGroup((String) req.getAttribute(ContextKeyConstants.USING_GROUP));
        info.setUserGroup((String) req.getAttribute(ContextKeyConstants.USER_GROUP));
        String autoGroup = (String) req.getAttribute(ContextKeyConstants.AUTO_GROUP);
        if (autoGroup != null && !autoGroup.isEmpty()) {
            info.setUsingGroup(autoGroup);
        }

        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("username", req.getAttribute("username"));
        extraData.put("channelName", req.getAttribute(ContextKeyConstants.CHANNEL_NAME));
        extraData.put("tokenName", req.getAttribute("token_name"));
        extraData.put("upstreamRequestId", req.getAttribute("upstream_request_id"));
        appendDispatchMetadata(extraData, context);
        info.setExtraData(extraData);

        String modelName = (String) req.getAttribute("original_model");
        if (modelName != null && !modelName.isEmpty()) {
            info.setOriginModelName(modelName);
        }

        parseRequestBody(req, info);
        return info;
    }

    private void appendDispatchMetadata(Map<String, Object> extraData, DispatchContext context) {
        if (context == null) {
            return;
        }
        if (context.source() != null) {
            extraData.put("dispatch_source", context.source().name());
        }
        if (context.sessionId() != null && !context.sessionId().isEmpty()) {
            extraData.put("dispatch_session_id", context.sessionId());
        }
        if (context.runId() != null && !context.runId().isEmpty()) {
            extraData.put("dispatch_run_id", context.runId());
        }
    }

    private String resolveRequestId(HttpServletRequest req, DispatchContext context) {
        if (context != null && context.requestId() != null && !context.requestId().isEmpty()) {
            return context.requestId();
        }
        String requestId = (String) req.getAttribute("request_id");
        if (requestId == null || requestId.isEmpty()) {
            requestId = System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        }
        return requestId;
    }

    private boolean parseModelMapping(HttpServletRequest req) {
        String mappingJson = (String) req.getAttribute(ContextKeyConstants.CHANNEL_MODEL_MAPPING);
        if (mappingJson != null && !mappingJson.isEmpty() && !"{}".equals(mappingJson)) {
            try {
                Map<String, Object> mapping = Convert.toJSONObject(mappingJson);
                String originModel = (String) req.getAttribute("original_model");
                if (originModel != null && mapping.containsKey(originModel)) {
                    String upstreamModel = mapping.get(originModel).toString();
                    if (!upstreamModel.isEmpty()) {
                        req.setAttribute("upstream_model_name", upstreamModel);
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("model_mapping 解析失败: {}", e.getMessage());
            }
        }
        return false;
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
                body = Convert.toJSONObject(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
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

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = resolveString(baseUrl, null);
        if (normalized == null) {
            return null;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }
    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private String resolveString(Object preferred, Object fallback) {
        if (preferred instanceof String str && !str.isEmpty()) {
            return str;
        }
        if (fallback instanceof String str && !str.isEmpty()) {
            return str;
        }
        return null;
    }
}
