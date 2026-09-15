package aicrediflux.token.rag.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import aicrediflux.token.dispatch.DispatchSource;

public final class RagEmbeddingContextHolder {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private RagEmbeddingContextHolder() {
    }

    public static Context index(int userId, String group, String requestId) {
        return new Context(userId, groupOrDefault(group), requestIdOrCreate(requestId), DispatchSource.RAG_INDEX);
    }

    public static Context query(int userId, String group, String requestId) {
        return new Context(userId, groupOrDefault(group), requestIdOrCreate(requestId), DispatchSource.RAG_QUERY);
    }

    public static Optional<Context> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static <T> T withContext(Context context, Supplier<T> supplier) {
        Context previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void withContext(Context context, Runnable runnable) {
        withContext(context, () -> {
            runnable.run();
            return null;
        });
    }

    private static String groupOrDefault(String group) {
        return group == null || group.isBlank() ? "default" : group;
    }

    private static String requestIdOrCreate(String requestId) {
        return requestId == null || requestId.isBlank()
                ? "rag-" + UUID.randomUUID().toString().replace("-", "")
                : requestId;
    }

    public record Context(int userId, String group, String requestId, DispatchSource source) {
    }
}