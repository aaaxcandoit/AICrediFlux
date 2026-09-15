package aicrediflux.token.agent.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import ai.yue.library.base.convert.Convert;

import aicrediflux.token.agent.domain.AgentMessage;
import aicrediflux.token.constant.ContextKeyConstants;
import aicrediflux.token.dispatch.DispatchContext;
import aicrediflux.token.dispatch.ModelDispatchService;
import aicrediflux.token.dispatch.RelayInfoBuilder;
import aicrediflux.token.pojo.dto.Usage;
import aicrediflux.token.pojo.entity.Channel;
import aicrediflux.token.relay.common.RelayInfo;
import aicrediflux.token.relay.constant.RelayModeEnum;
import aicrediflux.token.service.ChannelSelectService;
import aicrediflux.token.service.ChannelService;
import aicrediflux.token.service.QuotaService;
import aicrediflux.token.service.RetryParam;

@Service
public class DefaultInternalChatService implements InternalChatService {
    private final ModelDispatchService dispatchService;
    private final RelayInfoBuilder relayInfoBuilder;
    private final ChannelService channelService;
    private final QuotaService quotaService;

    @Autowired
    public DefaultInternalChatService(ModelDispatchService dispatchService, RelayInfoBuilder relayInfoBuilder,
                                      ChannelService channelService, QuotaService quotaService) {
        this.dispatchService = dispatchService;
        this.relayInfoBuilder = relayInfoBuilder;
        this.channelService = channelService;
        this.quotaService = quotaService;
    }

    @Override
    public InternalChatResult chat(InternalChatRequest request) {
        Map<String, Object> body = buildBody(request);
        MemoryServletExchange exchange = new MemoryServletExchange("POST", "/v1/chat/completions", body);
        HttpServletRequest servletRequest = exchange.request();
        HttpServletResponse servletResponse = exchange.response();
        setupRequestAttributes(servletRequest, request, body);
        selectChannel(servletRequest, request);

        RelayInfo info = relayInfoBuilder.buildPlayground(servletRequest,
                DispatchContext.agent(request.userId(), groupOrDefault(request.group()), request.requestId())
                        .sessionId(request.sessionNo())
                        .runId(request.runNo()));
        info.setResponse(servletResponse);
        info.setRelayMode(RelayModeEnum.CHAT_COMPLETIONS);
        info.setPlayground(true);
        dispatchService.dispatchRelay(servletRequest, servletResponse, info, "openai");

        if (exchange.status() >= 400) {
            throw new IllegalStateException(extractError(exchange.body(), exchange.status()));
        }
        return parseResult(exchange.body(), info, request.requestId());
    }

    private Map<String, Object> buildBody(InternalChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("stream", request.stream());
        body.put("messages", request.messages().stream()
                .map(this::toOpenAiMessage)
                .toList());
        if (request.toolCallbacks() != null && !request.toolCallbacks().isEmpty()) {
            body.put("tools", request.toolCallbacks().stream().map(this::toOpenAiTool).toList());
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private Map<String, Object> toOpenAiMessage(InternalChatMessage message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", message.role());
        item.put("content", message.content() == null ? "" : message.content());
        if (AgentMessage.ROLE_TOOL.equals(message.role())) {
            item.put("tool_call_id", message.toolCallId());
            if (message.name() != null && !message.name().isBlank()) {
                item.put("name", message.name());
            }
        }
        if (AgentMessage.ROLE_ASSISTANT.equals(message.role()) && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            item.put("tool_calls", message.toolCalls().stream().map(call -> {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", call.name());
                function.put("arguments", call.arguments() == null ? "{}" : call.arguments());
                Map<String, Object> toolCall = new LinkedHashMap<>();
                toolCall.put("id", call.id());
                toolCall.put("type", call.type() == null || call.type().isBlank() ? "function" : call.type());
                toolCall.put("function", function);
                return toolCall;
            }).toList());
        }
        return item;
    }

    private Map<String, Object> toOpenAiTool(ToolCallback callback) {
        ToolDefinition definition = callback.getToolDefinition();
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", definition.name());
        function.put("description", definition.description());
        function.put("parameters", parseSchema(definition.inputSchema()));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private Object parseSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            return Convert.toJSONObject(schema);
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private void setupRequestAttributes(HttpServletRequest servletRequest, InternalChatRequest request, Map<String, Object> body) {
        String group = groupOrDefault(request.group());
        servletRequest.setAttribute(ContextKeyConstants.USER_ID, request.userId());
        servletRequest.setAttribute(ContextKeyConstants.USER_GROUP, group);
        servletRequest.setAttribute(ContextKeyConstants.USING_GROUP, group);
        servletRequest.setAttribute(ContextKeyConstants.TOKEN_GROUP, group);
        servletRequest.setAttribute(ContextKeyConstants.TOKEN_ID, 0);
        servletRequest.setAttribute(ContextKeyConstants.TOKEN_KEY, "agent-" + request.sessionNo());
        servletRequest.setAttribute("token_name", "AICrediFlux Copilot");
        servletRequest.setAttribute(ContextKeyConstants.ORIGINAL_MODEL, request.model());
        servletRequest.setAttribute("request_id", request.requestId());
        servletRequest.setAttribute(ContextKeyConstants.REQUEST_START_TIME, System.currentTimeMillis());
        servletRequest.setAttribute(ContextKeyConstants.PARSED_REQUEST_BODY, body);
    }

    private void selectChannel(HttpServletRequest servletRequest, InternalChatRequest request) {
        RetryParam retryParam = new RetryParam(servletRequest, groupOrDefault(request.group()), request.model());
        Object[] selected = ChannelSelectService.cacheGetRandomSatisfiedChannel(retryParam, channelService, null);
        Channel channel = (Channel) selected[0];
        if (channel == null) {
            throw new IllegalStateException("no available channel in group " + groupOrDefault(request.group())
                    + " for model " + request.model());
        }
        servletRequest.setAttribute(ContextKeyConstants.USING_GROUP, selected[1]);
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_ID, channel.getId());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_NAME, channel.getName());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_TYPE, channel.getType());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_CREATE_TIME, channel.getCreatedTime());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_SETTING, channel.getSetting());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_OTHER_SETTING, channel.getOtherInfo());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_MODEL_MAPPING, channel.getModelMapping());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_STATUS_CODE_MAPPING, channel.getStatusCodeMapping());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_AUTO_BAN, channel.getAutoBan());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_HEADER_OVERRIDE, channel.getHeaderOverride());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_PARAM_OVERRIDE, channel.getParamOverride());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_ORGANIZATION, channel.getOpenaiOrganization());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_KEY, firstKey(channel.getKey()));
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_BASE_URL, channel.getBaseUrl());
        servletRequest.setAttribute(ContextKeyConstants.CHANNEL_IS_MULTI_KEY, false);
    }

    @SuppressWarnings("unchecked")
    private InternalChatResult parseResult(String responseBody, RelayInfo info, String requestId) {
        Map<String, Object> json = Convert.toJSONObject(responseBody);
        String content = "";
        Object choicesObj = json.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty() && choices.get(0) instanceof Map<?, ?> first) {
            Object messageObj = ((Map<String, Object>) first).get("message");
            if (messageObj instanceof Map<?, ?> message) {
                Object contentObj = ((Map<String, Object>) message).get("content");
                content = contentObj == null ? "" : contentObj.toString();
            }
        }
        List<InternalChatToolCall> toolCalls = extractToolCalls(json);

        Usage usage = new Usage();
        Object usageObj = json.get("usage");
        if (usageObj instanceof Map<?, ?> usageMap) {
            usage.setPromptTokens(toInt(((Map<String, Object>) usageMap).get("prompt_tokens")));
            usage.setCompletionTokens(toInt(((Map<String, Object>) usageMap).get("completion_tokens")));
            usage.setTotalTokens(toInt(((Map<String, Object>) usageMap).get("total_tokens")));
        }
        int quota = usage.getPromptTokens() + usage.getCompletionTokens() > 0
                ? quotaService.calculateTextQuotaWithCache(info, usage)
                : 0;
        return new InternalChatResult(content, usage.getPromptTokens(), usage.getCompletionTokens(), quota,
                requestId, info.getChannelId(), toolCalls);
    }


    @SuppressWarnings("unchecked")
    private List<InternalChatToolCall> extractToolCalls(Map<String, Object> json) {
        Object choicesObj = json.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> first)) {
            return List.of();
        }
        Object messageObj = ((Map<String, Object>) first).get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            return List.of();
        }
        Object toolCallsObj = ((Map<String, Object>) message).get("tool_calls");
        if (!(toolCallsObj instanceof List<?> toolCalls)) {
            return List.of();
        }
        List<InternalChatToolCall> result = new java.util.ArrayList<>();
        for (Object item : toolCalls) {
            if (!(item instanceof Map<?, ?> toolCall)) {
                continue;
            }
            Map<String, Object> callMap = (Map<String, Object>) toolCall;
            Object functionObj = callMap.get("function");
            String name = "";
            String arguments = "{}";
            if (functionObj instanceof Map<?, ?> function) {
                Object nameObj = ((Map<String, Object>) function).get("name");
                Object argsObj = ((Map<String, Object>) function).get("arguments");
                name = nameObj == null ? "" : nameObj.toString();
                arguments = argsObj == null ? "{}" : argsObj.toString();
            }
            result.add(new InternalChatToolCall(
                    String.valueOf(callMap.getOrDefault("id", "")),
                    String.valueOf(callMap.getOrDefault("type", "function")),
                    name,
                    arguments));
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private String extractError(String body, int status) {
        if (body == null || body.isBlank()) {
            return "Copilot model call failed, status=" + status;
        }
        try {
            Map<String, Object> json = Convert.toJSONObject(body);
            Object error = json.get("error");
            if (error instanceof Map<?, ?> err) {
                Object message = ((Map<String, Object>) err).get("message");
                if (message != null) {
                    return message.toString();
                }
            }
        } catch (Exception ignore) {
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private String groupOrDefault(String group) {
        return group == null || group.isBlank() ? "default" : group;
    }

    private String firstKey(String key) {
        if (key == null) {
            return "";
        }
        String trimmed = key.trim();
        int newline = trimmed.indexOf('\n');
        return newline > 0 ? trimmed.substring(0, newline).trim() : trimmed;
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static final class MemoryServletExchange {
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final byte[] bodyBytes;
        private final String method;
        private final String uri;
        private final StringWriter writerBuffer = new StringWriter();
        private final ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        private final PrintWriter writer = new PrintWriter(writerBuffer, true);
        private int status = 200;
        private String contentType = "application/json;charset=UTF-8";

        MemoryServletExchange(String method, String uri, Map<String, Object> body) {
            this.method = method;
            this.uri = uri;
            this.bodyBytes = Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8);
            this.headers.put("content-type", "application/json");
        }

        HttpServletRequest request() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "getAttribute" -> attributes.get((String) args[0]);
                case "setAttribute" -> {
                    attributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "getContentType" -> "application/json";
                case "getInputStream" -> new MemoryServletInputStream(bodyBytes);
                case "getReader" -> new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bodyBytes), StandardCharsets.UTF_8));
                case "getRequestURI" -> uri;
                case "getMethod" -> this.method;
                case "getHeader" -> headers.get(String.valueOf(args[0]).toLowerCase());
                case "getHeaderNames" -> Collections.enumeration(headers.keySet());
                case "getParameter" -> null;
                case "getRemoteAddr" -> "127.0.0.1";
                case "getScheme" -> "http";
                case "getServerName" -> "127.0.0.1";
                case "getServerPort" -> 9527;
                case "isSecure" -> false;
                default -> defaultValue(method.getReturnType());
            };
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class}, handler);
        }

        HttpServletResponse response() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "setStatus" -> {
                    status = (Integer) args[0];
                    yield null;
                }
                case "getStatus" -> status;
                case "setContentType" -> {
                    contentType = (String) args[0];
                    yield null;
                }
                case "getContentType" -> contentType;
                case "getWriter" -> writer;
                case "getOutputStream" -> new MemoryServletOutputStream(outputBuffer);
                case "getCharacterEncoding" -> "UTF-8";
                default -> defaultValue(method.getReturnType());
            };
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class}, handler);
        }

        int status() {
            return status;
        }

        String body() {
            writer.flush();
            String written = writerBuffer.toString();
            if (!written.isEmpty()) {
                return written;
            }
            return outputBuffer.toString(StandardCharsets.UTF_8);
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == Boolean.TYPE) return false;
            if (returnType == Integer.TYPE) return 0;
            if (returnType == Long.TYPE) return 0L;
            if (returnType == Double.TYPE) return 0D;
            if (returnType == Float.TYPE) return 0F;
            if (returnType == Void.TYPE) return null;
            if (Enumeration.class.isAssignableFrom(returnType)) return Collections.emptyEnumeration();
            return null;
        }
    }

    private static final class MemoryServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        MemoryServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
        }

        @Override
        public int read() {
            return delegate.read();
        }
    }

    private static final class MemoryServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream delegate;

        MemoryServletOutputStream(ByteArrayOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }
    }
}




