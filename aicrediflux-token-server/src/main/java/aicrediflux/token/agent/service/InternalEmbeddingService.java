package aicrediflux.token.agent.service;

public interface InternalEmbeddingService {
    InternalEmbeddingResult embed(InternalEmbeddingRequest request);
}