package aicrediflux.token.agent.service;

import java.util.List;

public record InternalEmbeddingResult(List<float[]> embeddings, int promptTokens, long quota,
                                      String requestId, Integer channelId) {
}