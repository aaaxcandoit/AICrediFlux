package aicrediflux.token.agent.service;

import aicrediflux.token.rag.service.RagRetrievalResult;

public interface AgentRunEventPublisher {
    void textDelta(String runNo, String content);

    void usage(String runNo, int promptTokens, int completionTokens, long quota);

    default void toolStart(String runNo, String toolName, String toolCallId) {
    }

    default void toolResult(String runNo, String toolName, String toolCallId, String status, Object result) {
    }

    default void ragRefs(String runNo, RagRetrievalResult result) {
    }

    void done(String runNo, String status);

    void error(String runNo, String message);
}
