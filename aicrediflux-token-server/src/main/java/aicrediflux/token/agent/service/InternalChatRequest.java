package aicrediflux.token.agent.service;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;

public record InternalChatRequest(int userId, String group, String model, List<InternalChatMessage> messages,
                                  String requestId, String sessionNo, String runNo, boolean stream,
                                  List<ToolCallback> toolCallbacks) {
    public InternalChatRequest(int userId, String group, String model, List<InternalChatMessage> messages,
                               String requestId, String sessionNo, String runNo, boolean stream) {
        this(userId, group, model, messages, requestId, sessionNo, runNo, stream, List.of());
    }
}
