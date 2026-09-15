package aicrediflux.token.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aicrediflux.token.rag.domain.RagChunk;
import aicrediflux.token.rag.domain.RagDocument;

@Primary
@Service
public class SpringAiRagVectorGateway implements RagVectorGateway {
    private static final Logger log = LoggerFactory.getLogger(SpringAiRagVectorGateway.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public SpringAiRagVectorGateway(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @Override
    public void indexDocument(RagDocument document, List<RagChunk> chunks) {
        try {
            RagEmbeddingContextHolder.withContext(
                    RagEmbeddingContextHolder.index(document.getCreatedBy() == null ? 0 : document.getCreatedBy(), "default", requestId("rag-index")),
                    () -> vectorStore().add(chunks.stream().map(chunk -> toDocument(document, chunk)).toList()));
        } catch (Exception e) {
            log.error("Failed to index RAG document into vector store, docNo={}, chunkCount={}",
                    document == null ? null : document.getDocNo(), chunks == null ? 0 : chunks.size(), e);
            throw unavailable("知识库索引失败", e);
        }
    }

    @Override
    public RagRetrievalResult search(int userId, String query, List<String> spaces, int topK) {
        try {
            List<Document> documents = RagEmbeddingContextHolder.withContext(
                    RagEmbeddingContextHolder.query(userId, "default", requestId("rag-query")),
                    () -> vectorStore().similaritySearch(buildSearchRequest(query, spaces, topK)));
            return RagRetrievalResult.available(toCitations(documents));
        } catch (Exception e) {
            return RagRetrievalResult.unavailable(rootMessage("知识库检索失败", e));
        }
    }

    @Override
    public void deleteDocument(String docNo) {
        try {
            vectorStore().delete(spaceFilter("docNo", docNo));
        } catch (Exception e) {
            throw unavailable("知识库向量删除失败", e);
        }
    }

    private VectorStore vectorStore() {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new RagUnavailableException("知识库暂不可用：Milvus VectorStore 尚未配置或未成功自动装配");
        }
        return vectorStore;
    }
    private SearchRequest buildSearchRequest(String query, List<String> spaces, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query == null ? "" : query)
                .topK(Math.max(topK, 1));
        Filter.Expression filter = spacesFilter(spaces);
        if (filter != null) {
            builder.filterExpression(filter);
        }
        return builder.build();
    }

    private Filter.Expression spacesFilter(List<String> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return null;
        }
        List<String> normalized = spaces.stream()
                .filter(space -> space != null && !space.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        if (normalized.size() == 1) {
            return builder.eq("space", normalized.get(0)).build();
        }
        List<Object> values = new ArrayList<>(normalized);
        return builder.in("space", values).build();
    }

    private Filter.Expression spaceFilter(String key, String value) {
        return new FilterExpressionBuilder().eq(key, value).build();
    }

    private Document toDocument(RagDocument document, RagChunk chunk) {
        return Document.builder()
                .id(chunk.getVectorId())
                .text(chunk.getText())
                .metadata(metadata(document, chunk))
                .build();
    }

    private Map<String, Object> metadata(RagDocument document, RagChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("space", document.getSpace());
        metadata.put("docNo", document.getDocNo());
        metadata.put("docId", docId(document));
        metadata.put("sourceName", document.getSourceName() == null ? "" : document.getSourceName());
        metadata.put("title", document.getTitle());
        metadata.put("contentHash", document.getContentHash());
        metadata.put("version", document.getVersion());
        metadata.put("chunkNo", chunk.getChunkNo());
        metadata.put("locator", chunk.getLocator() == null ? "" : chunk.getLocator());
        metadata.put("chunkHash", chunk.getContentHash());
        return metadata;
    }

    private List<RagCitation> toCitations(List<Document> documents) {
        List<RagCitation> citations = new ArrayList<>();
        int index = 1;
        for (Document document : documents == null ? List.<Document>of() : documents) {
            Map<String, Object> metadata = document.getMetadata();
            citations.add(new RagCitation("[" + index++ + "]",
                    string(metadata.get("title"), "未知文档"),
                    string(metadata.get("space"), "platform_docs"),
                    string(metadata.get("docNo"), string(metadata.get("docId"), "")),
                    string(metadata.get("docId"), ""),
                    string(metadata.get("sourceName"), ""),
                    string(metadata.get("locator"), ""),
                    snippet(document.getText()),
                    document.getScore() == null ? 0D : document.getScore()));
        }
        return citations;
    }

    private String docId(RagDocument document) {
        String sourceName = document.getSourceName();
        if (sourceName == null || sourceName.isBlank()) {
            return document.getDocNo();
        }
        String normalized = sourceName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        base = base.replaceFirst("(?i)\\.(md|txt)$", "");
        String prefix = document.getSpace() + "__";
        if (base.startsWith(prefix)) {
            return base.substring(prefix.length());
        }
        return base.isBlank() ? document.getDocNo() : base;
    }
    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }

    private String string(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private String requestId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private RagUnavailableException unavailable(String prefix, Exception e) {
        return new RagUnavailableException(rootMessage(prefix, e), e);
    }

    private String rootMessage(String prefix, Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return prefix + "：" + (message == null || message.isBlank() ? current.getClass().getSimpleName() : message);
    }
}

