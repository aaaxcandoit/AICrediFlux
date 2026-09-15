package aicrediflux.token.rag.service;

import java.util.List;

import aicrediflux.token.rag.domain.RagChunk;
import aicrediflux.token.rag.domain.RagDocument;

public interface RagVectorGateway {
    void indexDocument(RagDocument document, List<RagChunk> chunks);

    RagRetrievalResult search(int userId, String query, List<String> spaces, int topK);

    void deleteDocument(String docNo);
}
