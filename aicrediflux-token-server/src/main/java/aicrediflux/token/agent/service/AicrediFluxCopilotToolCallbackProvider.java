package aicrediflux.token.agent.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import aicrediflux.token.agent.domain.AgentToolLog;
import aicrediflux.token.agent.mapper.AgentRunMapper;
import aicrediflux.token.agent.mapper.AgentToolLogMapper;

@Component
public class AicrediFluxCopilotToolCallbackProvider {
    private static final Logger log = LoggerFactory.getLogger(AicrediFluxCopilotToolCallbackProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AicrediFluxCopilotTools tools;
    private final AicrediFluxToolInvocationContextHolder contextHolder;
    private final AgentToolLogMapper toolLogMapper;
    private final AgentRunMapper runMapper;
    private final AgentRunEventPublisher eventPublisher;
    private final AicrediFluxToolPolicy toolPolicy;
    private final Clock clock;

    @Autowired
    public AicrediFluxCopilotToolCallbackProvider(AicrediFluxCopilotTools tools,
                                                  AicrediFluxToolInvocationContextHolder contextHolder,
                                                  AgentToolLogMapper toolLogMapper,
                                                  AgentRunMapper runMapper,
                                                  AgentRunEventPublisher eventPublisher,
                                                  AicrediFluxToolPolicy toolPolicy) {
        this(tools, contextHolder, toolLogMapper, runMapper, eventPublisher, toolPolicy, Clock.systemUTC());
    }

    AicrediFluxCopilotToolCallbackProvider(AicrediFluxCopilotTools tools,
                                           AicrediFluxToolInvocationContextHolder contextHolder,
                                           AgentToolLogMapper toolLogMapper,
                                           AgentRunMapper runMapper,
                                           AgentRunEventPublisher eventPublisher,
                                           Clock clock) {
        this(tools, contextHolder, toolLogMapper, runMapper, eventPublisher, null, clock);
    }

    AicrediFluxCopilotToolCallbackProvider(AicrediFluxCopilotTools tools,
                                           AicrediFluxToolInvocationContextHolder contextHolder,
                                           AgentToolLogMapper toolLogMapper,
                                           AgentRunMapper runMapper,
                                           AgentRunEventPublisher eventPublisher,
                                           AicrediFluxToolPolicy toolPolicy,
                                           Clock clock) {
        this.tools = tools;
        this.contextHolder = contextHolder;
        this.toolLogMapper = toolLogMapper;
        this.runMapper = runMapper;
        this.eventPublisher = eventPublisher;
        this.toolPolicy = toolPolicy;
        this.clock = clock;
    }

    public List<ToolCallback> callbacks() {
        return List.of(ToolCallbacks.from(tools)).stream()
                .map(this::withAudit)
                .toList();
    }

    public List<ToolCallback> callbacksFor(AicrediFluxToolInvocationContext context, String latestQuestion) {
        return callbacks().stream()
                .filter(callback -> isAllowedForQuestion(callback.getToolDefinition().name(), context, latestQuestion))
                .toList();
    }

    ToolCallback withAudit(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                AicrediFluxToolInvocationContext context = fromToolContext(toolContext);
                String toolName = delegate.getToolDefinition().name();
                String toolCallId = toolCallId(toolContext, toolName);
                AgentToolLog toolLog = startLog(context, toolName, toolCallId, toolInput);
                eventPublisher.toolStart(context.runNo(), toolName, toolCallId);
                runMapper.markWaitingTool(context.runNo(), clock.instant());
                contextHolder.set(context);
                try {
                    String envelope = executeWithEnvelope(delegate, toolInput, toolContext, toolName);
                    ToolEnvelopeInfo info = readEnvelopeInfo(envelope);
                    finish(toolLog, info.status(), info.errorMessage(), envelope);
                    long latencyMs = latencyMs(toolLog);
                    if (AgentToolLog.STATUS_SUCCEEDED.equals(info.status())) {
                        eventPublisher.toolResult(context.runNo(), toolName, toolCallId, info.status(),
                                Map.of("result", toolLog.getOutputJson() == null ? "" : toolLog.getOutputJson(), "latencyMs", latencyMs));
                    } else {
                        eventPublisher.toolResult(context.runNo(), toolName, toolCallId, info.status(),
                                Map.of("error", info.errorMessage(), "result", toolLog.getOutputJson() == null ? "" : toolLog.getOutputJson(), "latencyMs", latencyMs));
                    }
                    return envelope;
                } finally {
                    contextHolder.clear();
                    runMapper.markRunning(context.runNo(), clock.instant());
                }
            }
        };
    }

    private String executeWithEnvelope(ToolCallback delegate, String toolInput, ToolContext toolContext, String toolName) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String result = delegate.call(toolInput, toolContext);
                return successEnvelope(toolName, result);
            } catch (RuntimeException e) {
                last = e;
                if (attempt == 0 && isTransient(e)) {
                    log.warn("Retry Copilot tool after transient failure, toolName={}", toolName, e);
                    continue;
                }
                break;
            }
        }
        String errorCode = classifyError(last);
        return errorEnvelope(toolName, errorCode, safeError(last == null ? null : last.getMessage()));
    }

    private boolean isAllowedForQuestion(String toolName, AicrediFluxToolInvocationContext context, String latestQuestion) {
        if (toolPolicy != null) {
            return toolPolicy.isAllowed(toolName, context, latestQuestion);
        }
        if (!"channelStatus".equals(toolName)) {
            return true;
        }
        return context != null && context.userId() > 0 && latestQuestion != null && latestQuestion.contains("当前渠道");
    }

    private AicrediFluxToolInvocationContext fromToolContext(ToolContext toolContext) {
        Map<String, Object> map = toolContext == null ? Map.of() : toolContext.getContext();
        return new AicrediFluxToolInvocationContext(
                intValue(map.get(AicrediFluxGatewayChatModel.CTX_USER_ID)),
                stringValue(map.get(AicrediFluxGatewayChatModel.CTX_GROUP), "default"),
                stringValue(map.get(AicrediFluxGatewayChatModel.CTX_SESSION_NO), ""),
                stringValue(map.get(AicrediFluxGatewayChatModel.CTX_RUN_NO), ""),
                stringValue(map.get(AicrediFluxGatewayChatModel.CTX_MODEL), ""));
    }

    private AgentToolLog startLog(AicrediFluxToolInvocationContext context, String toolName, String toolCallId, String inputJson) {
        Instant now = clock.instant();
        AgentToolLog toolLog = new AgentToolLog();
        toolLog.setToolLogNo("ATL" + UUID.randomUUID().toString().replace("-", ""));
        toolLog.setRunNo(context.runNo());
        toolLog.setSessionNo(context.sessionNo());
        toolLog.setUserId(context.userId());
        toolLog.setToolName(toolName);
        toolLog.setToolCallId(toolCallId);
        toolLog.setStatus(AgentToolLog.STATUS_RUNNING);
        toolLog.setInputJson(AgentToolPayloadSanitizer.sanitizeJson(toolInputToJson(inputJson)));
        toolLog.setStartedAt(now);
        toolLog.setCreateTime(now);
        toolLog.setUpdateTime(now);
        persistInsert(toolLog);
        return toolLog;
    }

    private void finish(AgentToolLog toolLog, String status, String errorMessage, String envelope) {
        Instant now = clock.instant();
        toolLog.setStatus(status);
        toolLog.setOutputJson(AgentToolPayloadSanitizer.sanitizeJson(envelope));
        toolLog.setErrorMessage(AgentToolLog.STATUS_SUCCEEDED.equals(status) ? null : safeError(errorMessage));
        toolLog.setFinishedAt(now);
        toolLog.setUpdateTime(now);
        persistUpdate(toolLog);
    }

    private void persistInsert(AgentToolLog toolLog) {
        try {
            toolLogMapper.insert(toolLog);
        } catch (RuntimeException e) {
            log.warn("Failed to persist Copilot tool log start, runNo={}, toolName={}",
                    toolLog.getRunNo(), toolLog.getToolName(), e);
        }
    }

    private void persistUpdate(AgentToolLog toolLog) {
        try {
            toolLogMapper.updateById(toolLog);
        } catch (RuntimeException e) {
            log.warn("Failed to persist Copilot tool log finish, runNo={}, toolName={}, status={}",
                    toolLog.getRunNo(), toolLog.getToolName(), toolLog.getStatus(), e);
        }
    }

    private long latencyMs(AgentToolLog toolLog) {
        if (toolLog.getStartedAt() == null || toolLog.getFinishedAt() == null) {
            return 0L;
        }
        return Math.max(0L, toolLog.getFinishedAt().toEpochMilli() - toolLog.getStartedAt().toEpochMilli());
    }

    private String toolCallId(ToolContext toolContext, String toolName) {
        if (toolContext == null || toolContext.getToolCallHistory() == null) {
            return "tool-" + UUID.randomUUID();
        }
        List<Message> history = toolContext.getToolCallHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            Message message = history.get(i);
            if (message instanceof AssistantMessage assistantMessage) {
                for (AssistantMessage.ToolCall call : assistantMessage.getToolCalls()) {
                    if (toolName.equals(call.name())) {
                        return call.id();
                    }
                }
            }
        }
        return "tool-" + UUID.randomUUID();
    }

    private String successEnvelope(String toolName, String rawResult) {
        ObjectNode root = JSON.createObjectNode();
        root.put("ok", true);
        root.put("tool", toolName);
        root.set("data", parseJsonOrText(AgentToolPayloadSanitizer.sanitizeJson(rawResult)));
        root.put("summary", summarize(rawResult));
        return root.toString();
    }

    private String errorEnvelope(String toolName, String errorCode, String message) {
        ObjectNode root = JSON.createObjectNode();
        root.put("ok", false);
        root.put("tool", toolName);
        root.put("errorCode", errorCode);
        root.put("message", message);
        return root.toString();
    }

    private JsonNode parseJsonOrText(String rawResult) {
        String value = rawResult == null ? "" : rawResult;
        try {
            return JSON.readTree(value);
        } catch (Exception ignored) {
            ObjectNode node = JSON.createObjectNode();
            node.put("text", value);
            return node;
        }
    }

    private String summarize(String rawResult) {
        String sanitized = AgentToolPayloadSanitizer.sanitizeText(rawResult == null ? "" : rawResult);
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }

    private ToolEnvelopeInfo readEnvelopeInfo(String envelope) {
        try {
            JsonNode node = JSON.readTree(envelope);
            boolean ok = node.path("ok").asBoolean(false);
            if (ok) {
                return new ToolEnvelopeInfo(AgentToolLog.STATUS_SUCCEEDED, "");
            }
            String errorCode = node.path("errorCode").asText("TOOL_ERROR");
            String status = "PERMISSION_DENIED".equals(errorCode) ? AgentToolLog.STATUS_DENIED : AgentToolLog.STATUS_FAILED;
            return new ToolEnvelopeInfo(status, safeError(node.path("message").asText("工具调用失败")));
        } catch (Exception e) {
            return new ToolEnvelopeInfo(AgentToolLog.STATUS_FAILED, "工具调用结果解析失败");
        }
    }

    private String classifyError(RuntimeException e) {
        if (e == null) {
            return "TOOL_ERROR";
        }
        String text = (e.getClass().getName() + " " + e.getMessage()).toLowerCase();
        if (text.contains("权限") || text.contains("仅管理员") || text.contains("permission")
                || text.contains("forbidden") || text.contains("unauthorized")) {
            return "PERMISSION_DENIED";
        }
        if (e instanceof IllegalArgumentException || text.contains("invalid") || text.contains("参数")) {
            return "INVALID_ARGUMENT";
        }
        return "TOOL_ERROR";
    }

    private boolean isTransient(RuntimeException e) {
        if (e == null) {
            return false;
        }
        String text = e.getClass().getName().toLowerCase() + " " + String.valueOf(e.getMessage()).toLowerCase();
        return text.contains("transient") || text.contains("timeout") || text.contains("deadlock")
                || text.contains("cannotgetjdbcconnection") || text.contains("connection reset");
    }

    private String toolInputToJson(String inputJson) {
        return inputJson == null || inputJson.isBlank() ? "{}" : inputJson;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        throw new IllegalStateException("Copilot 工具缺少用户上下文");
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }

    private String safeError(String message) {
        if (message == null || message.isBlank()) {
            return "工具调用失败";
        }
        String sanitized = AgentToolPayloadSanitizer.sanitizeText(message);
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private record ToolEnvelopeInfo(String status, String errorMessage) {
    }
}

