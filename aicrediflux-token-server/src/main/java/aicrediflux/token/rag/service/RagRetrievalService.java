package aicrediflux.token.rag.service;

import java.util.List;

import aicrediflux.token.agent.domain.AgentRun;

public interface RagRetrievalService {
    RagRetrievalResult retrieveForRun(AgentRun run, String userQuestion);

    default RagRetrievalResult search(int userId, String query, List<String> spaces, int topK) {
        return RagRetrievalResult.skipped();
    }

    static RagRetrievalService noop() {
        return (run, userQuestion) -> RagRetrievalResult.skipped();
    }
}
