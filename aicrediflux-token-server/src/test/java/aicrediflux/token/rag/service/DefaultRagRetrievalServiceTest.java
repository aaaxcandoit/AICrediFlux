package aicrediflux.token.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import aicrediflux.token.agent.domain.AgentRun;
import aicrediflux.token.rag.config.RagKnowledgeProperties;
import aicrediflux.token.rag.domain.RagChunk;
import aicrediflux.token.rag.domain.RagDocument;

class DefaultRagRetrievalServiceTest {

    @Test
    void retrieveForRunRoutesSseQuestionsToTroubleshooting() {
        CapturingGateway gateway = new CapturingGateway();
        RagKnowledgeProperties properties = new RagKnowledgeProperties();
        properties.setTopK(8);
        DefaultRagRetrievalService service = new DefaultRagRetrievalService(gateway, properties);
        AgentRun run = new AgentRun();
        run.setUserId(7);

        service.retrieveForRun(run, "SSE 里的 tool_start、rag_refs、usage 和 cancelled 分别是什么意思？");

        assertThat(gateway.query).contains("tool_start");
        assertThat(gateway.spaces).containsExactly("troubleshooting");
        assertThat(gateway.topK).isEqualTo(8);
    }

    @Test
    void explicitSearchUsesRequestedSpaceAndTopK() {
        CapturingGateway gateway = new CapturingGateway();
        DefaultRagRetrievalService service = new DefaultRagRetrievalService(gateway, new RagKnowledgeProperties());

        service.search(7, "SiliconFlow Base URL 怎么填？", List.of("platform_docs"), 5);

        assertThat(gateway.userId).isEqualTo(7);
        assertThat(gateway.query).contains("SiliconFlow");
        assertThat(gateway.spaces).containsExactly("platform_docs");
        assertThat(gateway.topK).isEqualTo(5);
    }

    private static final class CapturingGateway implements RagVectorGateway {
        private int userId;
        private String query;
        private List<String> spaces;
        private int topK;

        @Override
        public void indexDocument(RagDocument document, List<RagChunk> chunks) {
        }

        @Override
        public RagRetrievalResult search(int userId, String query, List<String> spaces, int topK) {
            this.userId = userId;
            this.query = query;
            this.spaces = spaces;
            this.topK = topK;
            return RagRetrievalResult.available(List.of());
        }

        @Override
        public void deleteDocument(String docNo) {
        }
    }
}
