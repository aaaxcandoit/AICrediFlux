package aicrediflux.token.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class AicrediFluxGatewayChatModel implements ChatModel {
    public static final String CTX_USER_ID = "aicrediflux.userId";
    public static final String CTX_GROUP = "aicrediflux.group";
    public static final String CTX_REQUEST_ID = "aicrediflux.requestId";
    public static final String CTX_SESSION_NO = "aicrediflux.sessionNo";
    public static final String CTX_RUN_NO = "aicrediflux.runNo";
    public static final String CTX_MODEL = "aicrediflux.model";
    public static final String META_QUOTA = "aicrediflux.quota";
    public static final String META_CHANNEL_ID = "aicrediflux.channelId";

    private static final int MAX_TOOL_ROUNDS = 8;

    private final InternalChatService internalChatService;
    private final ToolCallingManager toolCallingManager;
    private final AicrediFluxChatUsageHolder usageHolder;

    public AicrediFluxGatewayChatModel(InternalChatService internalChatService, AicrediFluxChatUsageHolder usageHolder) {
        this.internalChatService = internalChatService;
        this.usageHolder = usageHolder;
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        usageHolder.clear();
        AicrediFluxChatUsage aggregate = AicrediFluxChatUsage.empty();
        Prompt currentPrompt = prompt;
        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            InternalChatResult result = internalChatService.chat(toInternalRequest(currentPrompt));
            aggregate = aggregate.plus(result);
            ChatResponse response = toChatResponse(currentPrompt, result, aggregate);
            if (!response.hasToolCalls() || !ToolCallingChatOptions.isInternalToolExecutionEnabled(currentPrompt.getOptions())) {
                usageHolder.record(aggregate);
                return response;
            }
            if (round == MAX_TOOL_ROUNDS) {
                throw new IllegalStateException("Copilot 工具调用超过最大轮数 " + MAX_TOOL_ROUNDS);
            }
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(currentPrompt, response);
            if (toolExecutionResult.returnDirect()) {
                usageHolder.record(aggregate);
                return new ChatResponse(ToolExecutionResult.buildGenerations(toolExecutionResult), metadata(currentPrompt, aggregate));
            }
            currentPrompt = new Prompt(toolExecutionResult.conversationHistory(), currentPrompt.getOptions());
        }
        throw new IllegalStateException("Copilot 工具调用状态异常");
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return DefaultToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build();
    }

    private InternalChatRequest toInternalRequest(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        Map<String, Object> context = options instanceof ToolCallingChatOptions toolOptions
                ? toolOptions.getToolContext()
                : Map.of();
        String model = options == null ? null : options.getModel();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Copilot 模型不能为空");
        }
        List<ToolCallback> callbacks = options instanceof ToolCallingChatOptions toolOptions
                ? toolOptions.getToolCallbacks()
                : List.of();
        return new InternalChatRequest(
                intValue(context.get(CTX_USER_ID), "userId"),
                stringValue(context.get(CTX_GROUP), "default"),
                model,
                toInternalMessages(prompt.getInstructions()),
                stringValue(context.get(CTX_REQUEST_ID), "req-agent"),
                stringValue(context.get(CTX_SESSION_NO), ""),
                stringValue(context.get(CTX_RUN_NO), ""),
                false,
                callbacks);
    }

    private List<InternalChatMessage> toInternalMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<InternalChatMessage> result = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    result.add(InternalChatMessage.tool(response.id(), response.name(), response.responseData()));
                }
                continue;
            }
            String role = roleOf(message.getMessageType());
            if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
                result.add(InternalChatMessage.assistant(assistantMessage.getText(), assistantMessage.getToolCalls().stream()
                        .map(call -> new InternalChatToolCall(call.id(), call.type(), call.name(), call.arguments()))
                        .toList()));
            } else {
                result.add(new InternalChatMessage(role, message.getText()));
            }
        }
        return result;
    }

    private ChatResponse toChatResponse(Prompt prompt, InternalChatResult result, AicrediFluxChatUsage aggregate) {
        List<AssistantMessage.ToolCall> toolCalls = result.toolCalls() == null ? List.of() : result.toolCalls().stream()
                .map(call -> new AssistantMessage.ToolCall(call.id(), call.type(), call.name(), call.arguments()))
                .toList();
        AssistantMessage assistant = toolCalls.isEmpty() ? new AssistantMessage(result.content()) : new ToolCallAssistantMessage(result.content(), toolCalls);
        return new ChatResponse(List.of(new Generation(assistant)), metadata(prompt, aggregate));
    }

    private ChatResponseMetadata metadata(Prompt prompt, AicrediFluxChatUsage aggregate) {
        String model = prompt.getOptions() == null ? "" : prompt.getOptions().getModel();
        return ChatResponseMetadata.builder()
                .id(aggregate.requestId() == null ? "" : aggregate.requestId())
                .model(model == null ? "" : model)
                .usage(new SimpleUsage(aggregate.promptTokens(), aggregate.completionTokens()))
                .keyValue(META_QUOTA, aggregate.quota())
                .keyValue(META_CHANNEL_ID, aggregate.channelId())
                .build();
    }

    private String roleOf(MessageType type) {
        if (type == null) {
            return "user";
        }
        return type.getValue().toLowerCase(Locale.ROOT);
    }

    private int intValue(Object value, String name) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        throw new IllegalArgumentException("Copilot 缺少运行上下文: " + name);
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }

    private record SimpleUsage(Integer promptTokens, Integer completionTokens) implements Usage {
        @Override
        public Integer getPromptTokens() {
            return promptTokens;
        }

        @Override
        public Integer getCompletionTokens() {
            return completionTokens;
        }

        @Override
        public Object getNativeUsage() {
            return this;
        }
    }

    private static final class ToolCallAssistantMessage extends AssistantMessage {
        private final List<ToolCall> toolCalls;

        private ToolCallAssistantMessage(String content, List<ToolCall> toolCalls) {
            super(content == null ? "" : content);
            this.toolCalls = toolCalls == null ? List.of() : toolCalls;
        }

        @Override
        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        @Override
        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }
}

