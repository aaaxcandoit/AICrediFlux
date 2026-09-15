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
import java.util.ArrayList;
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

import ai.yue.library.base.convert.Convert;

import aicrediflux.token.constant.ContextKeyConstants;
import aicrediflux.token.dispatch.DispatchContext;
import aicrediflux.token.dispatch.DispatchSource;
import aicrediflux.token.dispatch.ModelDispatchService;
import aicrediflux.token.dispatch.RelayInfoBuilder;
import aicrediflux.token.pojo.dto.EmbeddingDTO;
import aicrediflux.token.pojo.dto.Usage;
import aicrediflux.token.pojo.entity.Channel;
import aicrediflux.token.relay.common.RelayInfo;
import aicrediflux.token.relay.constant.RelayModeEnum;
import aicrediflux.token.service.ChannelSelectService;
import aicrediflux.token.service.ChannelService;
import aicrediflux.token.service.QuotaService;
import aicrediflux.token.service.RetryParam;

@Service
public class DefaultInternalEmbeddingService implements InternalEmbeddingService {
    private final ModelDispatchService dispatchService;
    private final RelayInfoBuilder relayInfoBuilder;
    private final ChannelService channelService;
    private final QuotaService quotaService;

    @Autowired
    public DefaultInternalEmbeddingService(ModelDispatchService dispatchService, RelayInfoBuilder relayInfoBuilder,
                                           ChannelService channelService, QuotaService quotaService) {
        this.dispatchService = dispatchService;
        this.relayInfoBuilder = relayInfoBuilder;
        this.channelService = channelService;
        this.quotaService = quotaService;
    }

    @Override
    public InternalEmbeddingResult embed(InternalEmbeddingRequest request) {
        EmbeddingDTO embeddingRequest = new EmbeddingDTO(request.model(), request.inputs(), null, null,
                null, null, null, null, null, null);
        Map<String, Object> body = buildBody(embeddingRequest);
        MemoryServletExchange exchange = new MemoryServletExchange("POST", "/v1/embeddings", body);
        HttpServletRequest servletRequest = exchange.request();
        HttpServletResponse servletResponse = exchange.response();
        setupRequestAttributes(servletRequest, request, body);
        selectChannel(servletRequest, request);

        DispatchContext context = dispatchContext(request)
                .sessionId(null)
                .runId(null);
        RelayInfo info = relayInfoBuilder.buildPlayground(servletRequest, context);
        info.setResponse(servletResponse);
        info.setRelayMode(RelayModeEnum.EMBEDDINGS);
        info.setPlayground(true);
        info.setRequest(embeddingRequest);
        dispatchService.dispatchRelay(servletRequest, servletResponse, info, "openai");

        if (exchange.status() >= 400) {
            throw new IllegalStateException(extractError(exchange.body(), exchange.status()));
        }
        return parseResult(exchange.body(), info, request.requestId());
    }

    private DispatchContext dispatchContext(InternalEmbeddingRequest request) {
        DispatchSource source = request.source() == null ? DispatchSource.RAG_QUERY : request.source();
        if (source == DispatchSource.RAG_INDEX) {
            return DispatchContext.ragIndex(request.userId(), groupOrDefault(request.group()), request.requestId());
        }
        return DispatchContext.ragQuery(request.userId(), groupOrDefault(request.group()), request.requestId());
    }

    private Map<String, Object> buildBody(EmbeddingDTO request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("input", request.getInput());
        if (request.getDimensions() != null) {
            body.put("dimensions", request.getDimensions());
        }
        return body;
    }

    private void setupRequestAttributes(HttpServletRequest servletRequest, InternalEmbeddingRequest request, Map<String, Object> body) {
        String group = groupOrDefault(request.group());
        servletRequest.setAttribute(ContextKeyConstants.USER_ID, request.userId());
        servletRequest.setAttribute(ContextKeyConstants.USER_GROUP, group);
        servletRequest.setAttribute(ContextKeyConstants.USING_GROUP, group);
        servletRequest.setAttribute(ContextKeyConstants.TOKEN_GROUP, group);
        servletRequest.setAttribute(ContextKeyConstants.TOKEN_ID, 0);
        servletRequest.setAttribute(ContextKeyConstants.TOKEN_KEY, "rag-embedding");
        servletRequest.setAttribute("token_name", "AICrediFlux RAG");
        servletRequest.setAttribute(ContextKeyConstants.ORIGINAL_MODEL, request.model());
        servletRequest.setAttribute("request_id", request.requestId());
        servletRequest.setAttribute(ContextKeyConstants.REQUEST_START_TIME, System.currentTimeMillis());
        servletRequest.setAttribute(ContextKeyConstants.PARSED_REQUEST_BODY, body);
    }

    private void selectChannel(HttpServletRequest servletRequest, InternalEmbeddingRequest request) {
        RetryParam retryParam = new RetryParam(servletRequest, groupOrDefault(request.group()), request.model());
        Object[] selected = ChannelSelectService.cacheGetRandomSatisfiedChannel(retryParam, channelService, null);
        Channel channel = (Channel) selected[0];
        if (channel == null) {
            throw new IllegalStateException("no available channel in group " + groupOrDefault(request.group())
                    + " for embedding model " + request.model());
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
    private InternalEmbeddingResult parseResult(String responseBody, RelayInfo info, String requestId) {
        Map<String, Object> json = Convert.toJSONObject(responseBody);
        if (json == null) {
            throw new IllegalStateException("Embedding 上游响应为空或无法解析：" + snippet(responseBody));
        }
        List<float[]> embeddings = new ArrayList<>();
        Object dataObj = json.get("data");
        if (dataObj instanceof List<?> data) {
            for (Object item : data) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object embeddingObj = ((Map<String, Object>) map).get("embedding");
                if (!(embeddingObj instanceof List<?> values)) {
                    continue;
                }
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    Object value = values.get(i);
                    vector[i] = value instanceof Number number ? number.floatValue() : Float.parseFloat(String.valueOf(value));
                }
                embeddings.add(vector);
            }
        }
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("Embedding 上游未返回向量数据");
        }
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
        return new InternalEmbeddingResult(embeddings, usage.getPromptTokens(), quota, requestId, info.getChannelId());
    }

    @SuppressWarnings("unchecked")
    private String extractError(String body, int status) {
        if (body == null || body.isBlank()) {
            return "RAG embedding call failed, status=" + status;
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

    private String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() > 300 ? normalized.substring(0, 300) + "..." : normalized;
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
                case "setCharacterEncoding" -> null;
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