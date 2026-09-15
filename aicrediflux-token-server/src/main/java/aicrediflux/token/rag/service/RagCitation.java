package aicrediflux.token.rag.service;

public record RagCitation(String citationId, String title, String space, String docNo, String docId,
                          String sourceName, String locator, String snippet, double score) {
}
