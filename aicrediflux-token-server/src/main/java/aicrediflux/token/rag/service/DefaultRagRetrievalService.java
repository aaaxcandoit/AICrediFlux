package aicrediflux.token.rag.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import aicrediflux.token.agent.domain.AgentRun;
import aicrediflux.token.rag.config.RagKnowledgeProperties;

@Service
public class DefaultRagRetrievalService implements RagRetrievalService {
    private final RagVectorGateway vectorGateway;
    private final RagKnowledgeProperties properties;

    public DefaultRagRetrievalService(RagVectorGateway vectorGateway, RagKnowledgeProperties properties) {
        this.vectorGateway = vectorGateway;
        this.properties = properties;
    }

    @Override
    public RagRetrievalResult retrieveForRun(AgentRun run, String userQuestion) {
        if (!properties.isEnabled() || !shouldRetrieve(userQuestion)) {
            return RagRetrievalResult.skipped();
        }
        return search(run.getUserId(), userQuestion, inferSpaces(userQuestion), properties.getTopK());
    }

    @Override
    public RagRetrievalResult search(int userId, String query, List<String> spaces, int topK) {
        if (!properties.isEnabled()) {
            return RagRetrievalResult.skipped();
        }
        int safeTopK = topK <= 0 ? properties.getTopK() : Math.min(topK, 20);
        List<String> safeSpaces = spaces == null || spaces.isEmpty() ? inferSpaces(query) : spaces.stream()
                .filter(space -> space != null && !space.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return vectorGateway.search(userId, query, safeSpaces, safeTopK);
    }

    private boolean shouldRetrieve(String question) {
        String q = normalize(question);
        if (q.isBlank()) {
            return false;
        }
        return containsAny(q,
                "siliconflow", "api", "计费", "额度", "ai credit", "模型", "渠道", "错误", "报错",
                "no available channel", "倍率", "怎么", "如何", "文档", "配置",
                "sse", "run", "status", "text_delta", "tool_start", "tool_result", "rag_refs", "usage",
                "done", "error", "cancelled", "终态", "取消", "milvus", "embedding", "vectorstore", "索引");
    }

    private List<String> inferSpaces(String question) {
        String q = normalize(question);
        List<String> spaces = new ArrayList<>();
        if (containsAny(q, "sse", "run", "status", "text_delta", "tool_start", "tool_result", "rag_refs",
                "usage", "done", "error", "cancelled", "终态", "取消", "工具事件", "运行状态")) {
            spaces.add("troubleshooting");
        }
        if (containsAny(q, "rag", "索引", "milvus", "embedding", "vectorstore", "向量", "collection", "维度",
                "错误", "报错", "no available", "500", "401", "429")) {
            spaces.add("troubleshooting");
        }
        if (containsAny(q, "模型", "价格", "倍率", "费用", "deepseek", "model")) {
            spaces.add("model_docs");
        }
        if (containsAny(q, "siliconflow", "计费", "billing", "渠道配置", "base url", "供应商", "ai credit", "额度", "钱包")) {
            spaces.add("platform_docs");
        }
        if (spaces.isEmpty()) {
            spaces.add("platform_docs");
        }
        return spaces.stream().distinct().toList();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT);
    }
}
