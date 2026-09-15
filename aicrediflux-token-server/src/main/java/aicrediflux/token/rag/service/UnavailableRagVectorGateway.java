package aicrediflux.token.rag.service;

import java.util.List;

import org.springframework.stereotype.Service;

import aicrediflux.token.rag.domain.RagChunk;
import aicrediflux.token.rag.domain.RagDocument;

@Service
public class UnavailableRagVectorGateway implements RagVectorGateway {
    private static final String MESSAGE = "知识库暂不可用：Embedding 模型或 Milvus VectorStore 尚未配置";

    @Override
    public void indexDocument(RagDocument document, List<RagChunk> chunks) {
        throw new RagUnavailableException(MESSAGE);
    }

    @Override
    public RagRetrievalResult search(int userId, String query, List<String> spaces, int topK) {
        return RagRetrievalResult.unavailable(MESSAGE);
    }

    @Override
    public void deleteDocument(String docNo) {
    }
}
