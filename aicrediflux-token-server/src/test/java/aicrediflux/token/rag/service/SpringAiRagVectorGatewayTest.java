package aicrediflux.token.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.ai.vectorstore.filter.Filter;

import aicrediflux.token.dispatch.DispatchSource;
import aicrediflux.token.rag.domain.RagChunk;
import aicrediflux.token.rag.domain.RagDocument;

class SpringAiRagVectorGatewayTest {
    @Test
    void indexesChunksThroughSpringAiVectorStoreWithRagIndexContext() {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        SpringAiRagVectorGateway gateway = new SpringAiRagVectorGateway(provider(vectorStore));
        RagDocument document = document();
        RagChunk chunk = chunk(0, "# SiliconFlow\n配置 Base URL。", "h1:SiliconFlow");

        gateway.indexDocument(document, List.of(chunk));

        assertThat(vectorStore.added).hasSize(1);
        Document stored = vectorStore.added.get(0);
        assertThat(stored.getId()).isEqualTo("RD1:1:0");
        assertThat(stored.getText()).contains("Base URL");
        assertThat(stored.getMetadata()).containsEntry("space", "platform_docs")
                .containsEntry("docNo", "RD1")
                .containsEntry("docId", "siliconflow-onboarding")
                .containsEntry("sourceName", "platform_docs__siliconflow-onboarding.md")
                .containsEntry("title", "SiliconFlow Embedding 接入指南")
                .containsEntry("locator", "h1:SiliconFlow")
                .containsEntry("chunkNo", 0);
        assertThat(vectorStore.addContext.source()).isEqualTo(DispatchSource.RAG_INDEX);
        assertThat(vectorStore.addContext.userId()).isEqualTo(7);
    }

    @Test
    void searchesWithSpaceFilterAndMapsCitations() {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        vectorStore.results.add(Document.builder()
                .id("RD1:1:0")
                .text("SiliconFlow 的 Base URL 应填写 https://api.siliconflow.cn/v1。")
                .metadata(Map.of("space", "platform_docs", "docNo", "RD1", "docId", "siliconflow-onboarding", "sourceName", "platform_docs__siliconflow-onboarding.md", "title", "SiliconFlow Embedding 接入指南", "locator", "h1:SiliconFlow"))
                .score(0.91)
                .build());
        SpringAiRagVectorGateway gateway = new SpringAiRagVectorGateway(provider(vectorStore));

        RagRetrievalResult result = gateway.search(9, "怎么接入 SiliconFlow", List.of("platform_docs"), 8);

        assertThat(result.available()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).citationId()).isEqualTo("[1]");
        assertThat(result.citations().get(0).space()).isEqualTo("platform_docs");
        assertThat(result.citations().get(0).docId()).isEqualTo("siliconflow-onboarding");
        assertThat(result.citations().get(0).sourceName()).isEqualTo("platform_docs__siliconflow-onboarding.md");
        assertThat(result.citations().get(0).score()).isEqualTo(0.91);
        assertThat(vectorStore.lastRequest.getQuery()).isEqualTo("怎么接入 SiliconFlow");
        assertThat(vectorStore.lastRequest.getTopK()).isEqualTo(8);
        assertThat(vectorStore.lastRequest.getFilterExpression()).isNotNull();
        assertThat(vectorStore.searchContext.source()).isEqualTo(DispatchSource.RAG_QUERY);
        assertThat(vectorStore.searchContext.userId()).isEqualTo(9);
    }

    private ObjectProvider<VectorStore> provider(VectorStore vectorStore) {
        return new ObjectProvider<>() {
            @Override
            public VectorStore getObject(Object... args) {
                return vectorStore;
            }

            @Override
            public VectorStore getIfAvailable() {
                return vectorStore;
            }

            @Override
            public VectorStore getIfUnique() {
                return vectorStore;
            }

            @Override
            public VectorStore getObject() {
                return vectorStore;
            }
        };
    }
    private RagDocument document() {
        RagDocument document = new RagDocument();
        document.setDocNo("RD1");
        document.setSpace("platform_docs");
        document.setTitle("SiliconFlow Embedding 接入指南");
        document.setSourceName("platform_docs__siliconflow-onboarding.md");
        document.setContentHash("hash-doc");
        document.setVersion(1);
        document.setCreatedBy(7);
        return document;
    }

    private RagChunk chunk(int no, String text, String locator) {
        RagChunk chunk = new RagChunk();
        chunk.setDocNo("RD1");
        chunk.setVersion(1);
        chunk.setChunkNo(no);
        chunk.setVectorId("RD1:1:" + no);
        chunk.setText(text);
        chunk.setLocator(locator);
        chunk.setContentHash("hash-chunk-" + no);
        chunk.setCreateTime(Instant.parse("2026-09-14T00:00:00Z"));
        return chunk;
    }

    @Test
    void doesNotDependOnConditionalBeanOrdering() {
        assertThat(SpringAiRagVectorGateway.class.isAnnotationPresent(ConditionalOnBean.class)).isFalse();
    }
    private static final class CapturingVectorStore implements VectorStore {
        private final List<Document> added = new ArrayList<>();
        private final List<Document> results = new ArrayList<>();
        private SearchRequest lastRequest;
        private RagEmbeddingContextHolder.Context addContext;
        private RagEmbeddingContextHolder.Context searchContext;

        @Override
        public void add(List<Document> documents) {
            addContext = RagEmbeddingContextHolder.current().orElseThrow();
            added.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            searchContext = RagEmbeddingContextHolder.current().orElseThrow();
            lastRequest = request;
            return results;
        }
    }
}
