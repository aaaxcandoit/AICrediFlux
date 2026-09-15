package aicrediflux.token.agent.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import aicrediflux.token.agent.domain.AgentMessage;
import aicrediflux.token.agent.domain.AgentRun;
import aicrediflux.token.rag.service.RagRetrievalResult;

@Service
public class AgentSseService implements AgentRunEventPublisher {
    private static final long SSE_TIMEOUT_MILLIS = 125_000L;

    private final AgentConversationService conversationService;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public AgentSseService(AgentConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public SseEmitter streamRun(int userId, String runNo) {
        AgentRun run = conversationService.getOwnedRun(userId, runNo);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitter.onCompletion(() -> remove(runNo, emitter));
        emitter.onTimeout(() -> remove(runNo, emitter));
        emitter.onError(error -> remove(runNo, emitter));
        try {
            send(emitter, "status", Map.of(
                    "runNo", run.getRunNo(),
                    "status", run.getStatus(),
                    "model", run.getModel(),
                    "startedAt", nullSafe(run.getStartedAt())));
            if (AgentRun.ACTIVE_STATUSES.contains(run.getStatus())) {
                emitters.computeIfAbsent(runNo, key -> new CopyOnWriteArrayList<>()).add(emitter);
            } else {
                replayTerminalRun(userId, run, emitter);
                emitter.complete();
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public void textDelta(String runNo, String content) {
        publish(runNo, "text_delta", Map.of("runNo", runNo, "delta", content == null ? "" : content));
    }

    @Override
    public void usage(String runNo, int promptTokens, int completionTokens, long quota) {
        publish(runNo, "usage", Map.of(
                "runNo", runNo,
                "promptTokens", promptTokens,
                "completionTokens", completionTokens,
                "quota", quota));
    }

    @Override
    public void toolStart(String runNo, String toolName, String toolCallId) {
        publish(runNo, "tool_start", Map.of(
                "runNo", runNo,
                "toolName", safe(toolName),
                "toolCallId", safe(toolCallId)));
    }

    @Override
    public void toolResult(String runNo, String toolName, String toolCallId, String status, Object result) {
        publish(runNo, "tool_result", Map.of(
                "runNo", runNo,
                "toolName", safe(toolName),
                "toolCallId", safe(toolCallId),
                "status", safe(status),
                "result", result == null ? Map.of() : result));
    }


    @Override
    public void ragRefs(String runNo, RagRetrievalResult result) {
        publish(runNo, "rag_refs", result == null ? RagRetrievalResult.skipped() : result);
    }
    @Override
    public void done(String runNo, String status) {
        publish(runNo, "done", Map.of("runNo", runNo, "status", status));
        complete(runNo);
    }

    @Override
    public void error(String runNo, String message) {
        publish(runNo, "error", Map.of("runNo", runNo, "message", message == null ? "Copilot 运行失败" : message));
    }

    private void replayTerminalRun(int userId, AgentRun run, SseEmitter emitter) throws IOException {
        if (AgentRun.STATUS_COMPLETED.equals(run.getStatus()) || AgentRun.STATUS_SUCCEEDED.equals(run.getStatus())) {
            AgentMessage assistant = latestAssistant(conversationService.listMessages(userId, run.getSessionNo()));
            if (assistant != null && assistant.getContent() != null && !assistant.getContent().isEmpty()) {
                send(emitter, "text_delta", Map.of("runNo", run.getRunNo(), "delta", assistant.getContent()));
            }
            send(emitter, "usage", Map.of(
                    "runNo", run.getRunNo(),
                    "promptTokens", value(run.getTotalPromptTokens()),
                    "completionTokens", value(run.getTotalCompletionTokens()),
                    "quota", run.getTotalQuota() == null ? 0L : run.getTotalQuota()));
            send(emitter, "done", Map.of("runNo", run.getRunNo(), "status", run.getStatus()));
        } else if (AgentRun.STATUS_FAILED.equals(run.getStatus())) {
            send(emitter, "error", Map.of("runNo", run.getRunNo(), "message", safeError(run.getErrorMessage())));
            send(emitter, "done", Map.of("runNo", run.getRunNo(), "status", run.getStatus()));
        } else if (AgentRun.STATUS_CANCELLED.equals(run.getStatus())) {
            send(emitter, "done", Map.of("runNo", run.getRunNo(), "status", run.getStatus()));
        }
    }

    private AgentMessage latestAssistant(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (AgentMessage.ROLE_ASSISTANT.equals(message.getRole())) {
                return message;
            }
        }
        return null;
    }

    private void publish(String runNo, String event, Object data) {
        List<SseEmitter> list = emitters.get(runNo);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                send(emitter, event, data);
            } catch (IOException e) {
                remove(runNo, emitter);
                emitter.completeWithError(e);
            }
        }
    }

    private void complete(String runNo) {
        List<SseEmitter> list = emitters.remove(runNo);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            emitter.complete();
        }
    }

    private void remove(String runNo, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(runNo);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(runNo);
        }
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private String nullSafe(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeError(String message) {
        return message == null || message.isBlank() ? "Copilot 运行失败" : message;
    }
}

