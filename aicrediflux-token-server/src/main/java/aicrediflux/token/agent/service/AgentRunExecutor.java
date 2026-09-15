package aicrediflux.token.agent.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.yue.library.base.convert.Convert;

import aicrediflux.token.agent.domain.AgentMessage;
import aicrediflux.token.agent.domain.AgentRun;
import aicrediflux.token.agent.mapper.AgentMessageMapper;
import aicrediflux.token.agent.mapper.AgentRunMapper;
import aicrediflux.token.rag.service.RagCitation;
import aicrediflux.token.rag.service.RagRetrievalResult;
import aicrediflux.token.rag.service.RagRetrievalService;

@Service
public class AgentRunExecutor {
    private static final int MAX_CONTEXT_MESSAGES = 16;

    private final AgentRunMapper runMapper;
    private final AgentMessageMapper messageMapper;
    private final AicrediFluxGatewayChatModel chatModel;
    private final AicrediFluxCopilotToolCallbackProvider toolCallbackProvider;
    private final AicrediFluxChatUsageHolder usageHolder;
    private final AgentRunEventPublisher eventPublisher;
    private final RagRetrievalService ragRetrievalService;
    private final Clock clock;

    @Autowired
    public AgentRunExecutor(AgentRunMapper runMapper, AgentMessageMapper messageMapper,
                            AicrediFluxGatewayChatModel chatModel,
                            AicrediFluxCopilotToolCallbackProvider toolCallbackProvider,
                            AicrediFluxChatUsageHolder usageHolder,
                            AgentRunEventPublisher eventPublisher,
                            RagRetrievalService ragRetrievalService) {
        this(runMapper, messageMapper, chatModel, toolCallbackProvider, usageHolder, eventPublisher, ragRetrievalService, Clock.systemUTC());
    }

    AgentRunExecutor(AgentRunMapper runMapper, AgentMessageMapper messageMapper,
                     AicrediFluxGatewayChatModel chatModel,
                     AicrediFluxCopilotToolCallbackProvider toolCallbackProvider,
                     AicrediFluxChatUsageHolder usageHolder,
                     AgentRunEventPublisher eventPublisher,
                     Clock clock) {
        this(runMapper, messageMapper, chatModel, toolCallbackProvider, usageHolder, eventPublisher,
                RagRetrievalService.noop(), clock);
    }

    AgentRunExecutor(AgentRunMapper runMapper, AgentMessageMapper messageMapper,
                     AicrediFluxGatewayChatModel chatModel,
                     AicrediFluxCopilotToolCallbackProvider toolCallbackProvider,
                     AicrediFluxChatUsageHolder usageHolder,
                     AgentRunEventPublisher eventPublisher,
                     RagRetrievalService ragRetrievalService,
                     Clock clock) {
        this.runMapper = runMapper;
        this.messageMapper = messageMapper;
        this.chatModel = chatModel;
        this.toolCallbackProvider = toolCallbackProvider;
        this.usageHolder = usageHolder;
        this.eventPublisher = eventPublisher;
        this.ragRetrievalService = ragRetrievalService;
        this.clock = clock;
    }

    @Async
    public void executeAsync(String runNo) {
        execute(runNo);
    }

    @Transactional
    public void execute(String runNo) {
        AgentRun run = runMapper.selectByRunNo(runNo);
        if (run == null || !AgentRun.ACTIVE_STATUSES.contains(run.getStatus())) {
            return;
        }
        if (Integer.valueOf(1).equals(run.getStopRequested())) {
            runMapper.finish(runNo, AgentRun.STATUS_CANCELLED, null, clock.instant());
            eventPublisher.done(runNo, AgentRun.STATUS_CANCELLED);
            return;
        }
        try {
            ChatResponse response = callSpringAi(run);
            AicrediFluxChatUsage usage = usageHolder.current();
            String content = responseContent(response);
            if (content != null && !content.isBlank()) {
                saveAssistantMessage(run, content, usage);
                eventPublisher.textDelta(runNo, content);
            }
            eventPublisher.usage(runNo, usage.promptTokens(), usage.completionTokens(), usage.quota());
            int updated = runMapper.completeSuccess(runNo, usage.promptTokens(), usage.completionTokens(),
                    usage.quota(), clock.instant());
            if (updated != 1) {
                throw new IllegalStateException("更新 Copilot 运行成功状态失败");
            }
            eventPublisher.done(runNo, AgentRun.STATUS_COMPLETED);
        } catch (Exception e) {
            String message = trimError(e.getMessage());
            runMapper.finish(runNo, AgentRun.STATUS_FAILED, message, clock.instant());
            eventPublisher.error(runNo, message);
            eventPublisher.done(runNo, AgentRun.STATUS_FAILED);
        } finally {
            usageHolder.clear();
        }
    }

    private ChatResponse callSpringAi(AgentRun run) {
        List<AgentMessage> history = messageMapper.selectBySessionNo(run.getSessionNo());
        List<Message> messages = new ArrayList<>(buildContext(history));
        RagRetrievalResult rag = ragRetrievalService.retrieveForRun(run, latestUserQuestion(history));
        if (rag.shouldEmit()) {
            eventPublisher.ragRefs(run.getRunNo(), rag);
        }
        if (rag.available() && !rag.citations().isEmpty()) {
            messages.add(0, new SystemMessage(buildRagSystemPrompt(rag)));
        }
        AicrediFluxToolInvocationContext toolContext = new AicrediFluxToolInvocationContext(run.getUserId(), "default", run.getSessionNo(), run.getRunNo(), run.getModel());
        List<ToolCallback> callbacks = toolCallbackProvider.callbacksFor(toolContext, latestUserQuestion(history));
        DefaultToolCallingChatOptions options = (DefaultToolCallingChatOptions) DefaultToolCallingChatOptions.builder()
                .model(run.getModel())
                .internalToolExecutionEnabled(true)
                .toolCallbacks(callbacks)
                .toolContext(Map.of(
                        AicrediFluxGatewayChatModel.CTX_USER_ID, run.getUserId(),
                        AicrediFluxGatewayChatModel.CTX_GROUP, "default",
                        AicrediFluxGatewayChatModel.CTX_REQUEST_ID, run.getRequestId(),
                        AicrediFluxGatewayChatModel.CTX_SESSION_NO, run.getSessionNo(),
                        AicrediFluxGatewayChatModel.CTX_RUN_NO, run.getRunNo(),
                        AicrediFluxGatewayChatModel.CTX_MODEL, run.getModel()))
                .build();
        Prompt prompt = new Prompt(messages, options);
        return ChatClient.builder(chatModel).build().prompt(prompt).call().chatResponse();
    }

    private List<Message> buildContext(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<AgentMessage> filtered = messages.stream()
                .filter(m -> AgentMessage.ROLE_USER.equals(m.getRole()) || AgentMessage.ROLE_ASSISTANT.equals(m.getRole()))
                .toList();
        return filtered.stream()
                .skip(Math.max(0, filtered.size() - MAX_CONTEXT_MESSAGES))
                .map(this::toSpringMessage)
                .toList();
    }

    private Message toSpringMessage(AgentMessage message) {
        if (AgentMessage.ROLE_ASSISTANT.equals(message.getRole())) {
            return new AssistantMessage(message.getContent() == null ? "" : message.getContent());
        }
        return new UserMessage(message.getContent() == null ? "" : message.getContent());
    }

    private String latestUserQuestion(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (AgentMessage.ROLE_USER.equals(message.getRole())) {
                return message.getContent() == null ? "" : message.getContent();
            }
        }
        return "";
    }

    private String buildRagSystemPrompt(RagRetrievalResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("RAG 引用资料如下。回答平台规则、配置、计费或排障问题时优先依据这些资料；如果资料不足，请明确说明未找到依据。\n");
        int index = 1;
        for (RagCitation citation : result.citations()) {
            builder.append('\n').append('[').append(index++).append("] ")
                    .append(citation.title()).append(" | ").append(citation.space()).append(" | ")
                    .append(citation.locator()).append('\n')
                    .append(citation.snippet());
        }
        return builder.toString();
    }

    private String responseContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private void saveAssistantMessage(AgentRun run, String content, AicrediFluxChatUsage usage) {
        AgentMessage message = new AgentMessage();
        message.setMessageNo("AM" + UUID.randomUUID().toString().replace("-", ""));
        message.setSessionNo(run.getSessionNo());
        message.setUserId(run.getUserId());
        message.setRole(AgentMessage.ROLE_ASSISTANT);
        message.setContent(content);
        message.setUsageJson(Convert.toJSONString(new UsageSummary(usage.promptTokens(), usage.completionTokens(), usage.quota())));
        message.setCreateTime(Instant.now(clock));
        if (messageMapper.insert(message) != 1) {
            throw new IllegalStateException("保存 Copilot 回复失败");
        }
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return "Copilot 运行失败";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record UsageSummary(int promptTokens, int completionTokens, long quota) {
    }
}


