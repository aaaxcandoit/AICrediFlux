package aicrediflux.token.agent.service;

import java.util.List;

public record InternalChatMessage(String role, String content, String toolCallId, String name,
                                  List<InternalChatToolCall> toolCalls) {
    public InternalChatMessage(String role, String content) {
        this(role, content, null, null, List.of());
    }

    public static InternalChatMessage tool(String toolCallId, String name, String content) {
        return new InternalChatMessage("tool", content, toolCallId, name, List.of());
    }

    public static InternalChatMessage assistant(String content, List<InternalChatToolCall> toolCalls) {
        return new InternalChatMessage("assistant", content, null, null, toolCalls == null ? List.of() : toolCalls);
    }
}
