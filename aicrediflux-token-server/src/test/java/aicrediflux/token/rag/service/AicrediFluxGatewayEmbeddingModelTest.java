package aicrediflux.token.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;

import aicrediflux.token.agent.service.InternalEmbeddingRequest;
import aicrediflux.token.agent.service.InternalEmbeddingResult;
import aicrediflux.token.agent.service.InternalEmbeddingService;
import aicrediflux.token.dispatch.DispatchSource;
import aicrediflux.token.rag.config.RagKnowledgeProperties;

class AicrediFluxGatewayEmbeddingModelTest {
    @Test
    void delegatesSpringAiEmbeddingToInternalDispatchWithRagContext() {
        InternalEmbeddingService internal = org.mockito.Mockito.mock(InternalEmbeddingService.class);
        when(internal.embed(any())).thenReturn(new InternalEmbeddingResult(
                List.of(new float[]{0.1f, 0.2f, 0.3f}), 3, 3L, "req-rag-1", 8));
        RagKnowledgeProperties properties = new RagKnowledgeProperties();
        properties.setEmbeddingModel("Qwen/Qwen3-Embedding-0.6B");
        properties.setEmbeddingDimension(1024);

        AicrediFluxGatewayEmbeddingModel model = new AicrediFluxGatewayEmbeddingModel(properties, internal);
        EmbeddingResponse response = RagEmbeddingContextHolder.withContext(
                RagEmbeddingContextHolder.index(7, "default", "req-rag-1"),
                () -> model.call(new EmbeddingRequest(List.of("hello rag"), null)));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResult().getOutput()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(model.dimensions()).isEqualTo(1024);

        ArgumentCaptor<InternalEmbeddingRequest> captor = ArgumentCaptor.forClass(InternalEmbeddingRequest.class);
        verify(internal).embed(captor.capture());
        InternalEmbeddingRequest request = captor.getValue();
        assertThat(request.userId()).isEqualTo(7);
        assertThat(request.group()).isEqualTo("default");
        assertThat(request.model()).isEqualTo("Qwen/Qwen3-Embedding-0.6B");
        assertThat(request.inputs()).containsExactly("hello rag");
        assertThat(request.requestId()).isEqualTo("req-rag-1");
        assertThat(request.source()).isEqualTo(DispatchSource.RAG_INDEX);
    }
    @Test
    void isPrimaryEmbeddingModelForVectorStoreAutoConfiguration() {
        assertThat(AicrediFluxGatewayEmbeddingModel.class.isAnnotationPresent(Primary.class)).isTrue();
    }
}