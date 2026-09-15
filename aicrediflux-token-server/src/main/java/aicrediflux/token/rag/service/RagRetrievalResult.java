package aicrediflux.token.rag.service;

import java.util.List;

public record RagRetrievalResult(boolean skip, boolean available, String message, List<RagCitation> citations) {
    public static RagRetrievalResult skipped() {
        return new RagRetrievalResult(true, false, "", List.of());
    }

    public static RagRetrievalResult unavailable(String message) {
        return new RagRetrievalResult(false, false, message == null ? "知识库暂不可用" : message, List.of());
    }

    public static RagRetrievalResult available(List<RagCitation> citations) {
        return new RagRetrievalResult(false, true, "", citations == null ? List.of() : List.copyOf(citations));
    }

    public boolean shouldEmit() {
        return !skip;
    }
}
