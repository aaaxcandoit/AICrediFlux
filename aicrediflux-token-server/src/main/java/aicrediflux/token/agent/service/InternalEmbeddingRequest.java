package aicrediflux.token.agent.service;

import java.util.List;

import aicrediflux.token.dispatch.DispatchSource;

public record InternalEmbeddingRequest(int userId, String group, String model, List<String> inputs,
                                       String requestId, DispatchSource source) {
}