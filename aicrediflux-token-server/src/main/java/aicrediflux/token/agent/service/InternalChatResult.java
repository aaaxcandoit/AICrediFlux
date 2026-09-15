package aicrediflux.token.agent.service;

import java.util.List;

public record InternalChatResult(String content, int promptTokens, int completionTokens, long quota,
                                 String requestId, Integer channelId, List<InternalChatToolCall> toolCalls) {
    public InternalChatResult(String content, int promptTokens, int completionTokens, long quota,
                              String requestId, Integer channelId) {
        this(content, promptTokens, completionTokens, quota, requestId, channelId, List.of());
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
