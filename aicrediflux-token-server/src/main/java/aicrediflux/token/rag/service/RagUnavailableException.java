package aicrediflux.token.rag.service;

public class RagUnavailableException extends RuntimeException {
    public RagUnavailableException(String message) {
        super(message);
    }

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}