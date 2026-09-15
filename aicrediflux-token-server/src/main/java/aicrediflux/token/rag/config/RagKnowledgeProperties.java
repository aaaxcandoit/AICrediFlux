package aicrediflux.token.rag.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aicrediflux.rag")
public class RagKnowledgeProperties {
    private boolean enabled = true;
    private int chunkSize = 600;
    private int chunkOverlap = 100;
    private int topK = 8;
    private int maxFileBytes = 524288;
    private int embeddingDimension = 1024;
    private String embeddingModel = "";
    private Set<String> spaces = Set.of("platform_docs", "model_docs", "troubleshooting");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public int getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(int maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int embeddingDimension) { this.embeddingDimension = embeddingDimension; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Set<String> getSpaces() { return spaces; }
    public void setSpaces(Set<String> spaces) { this.spaces = spaces; }
}