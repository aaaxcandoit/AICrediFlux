package aicrediflux.token.rag.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import aicrediflux.token.agent.service.InternalEmbeddingRequest;
import aicrediflux.token.agent.service.InternalEmbeddingResult;
import aicrediflux.token.agent.service.InternalEmbeddingService;
import aicrediflux.token.rag.config.RagKnowledgeProperties;

@Primary
@Service
public class AicrediFluxGatewayEmbeddingModel implements EmbeddingModel {
    private final RagKnowledgeProperties properties;
    private final InternalEmbeddingService internalEmbeddingService;

    public AicrediFluxGatewayEmbeddingModel(RagKnowledgeProperties properties,
                                            InternalEmbeddingService internalEmbeddingService) {
        this.properties = properties;
        this.internalEmbeddingService = internalEmbeddingService;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (properties.getEmbeddingModel() == null || properties.getEmbeddingModel().isBlank()) {
            throw new RagUnavailableException("Embedding 模型未配置：请先在渠道中配置可用的 embeddings 模型，并设置 aicrediflux.rag.embedding-model");
        }
        RagEmbeddingContextHolder.Context context = RagEmbeddingContextHolder.current()
                .orElseThrow(() -> new RagUnavailableException("RAG Embedding 上下文缺失：索引或检索必须通过知识库服务发起"));
        List<String> inputs = request == null || request.getInstructions() == null ? List.of() : request.getInstructions();
        if (inputs.isEmpty()) {
            throw new RagUnavailableException("Embedding 输入不能为空");
        }
        InternalEmbeddingResult result = internalEmbeddingService.embed(new InternalEmbeddingRequest(
                context.userId(), context.group(), properties.getEmbeddingModel(), inputs, context.requestId(), context.source()));
        AtomicInteger index = new AtomicInteger();
        return new EmbeddingResponse(result.embeddings().stream()
                .map(vector -> new Embedding(vector, index.getAndIncrement()))
                .toList());
    }

    @Override
    public float[] embed(Document document) {
        return embed(document == null ? "" : document.getText());
    }

    @Override
    public int dimensions() {
        return properties.getEmbeddingDimension();
    }
}