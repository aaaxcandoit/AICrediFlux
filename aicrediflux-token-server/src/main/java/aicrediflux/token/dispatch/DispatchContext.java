package aicrediflux.token.dispatch;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class DispatchContext {

    private final DispatchSource source;
    private final int userId;
    private final String group;
    private final String requestId;
    private Integer tokenId;
    private String sessionId;
    private String runId;

    private DispatchContext(DispatchSource source, int userId, String group, String requestId) {
        this.source = source;
        this.userId = userId;
        this.group = group;
        this.requestId = requestId;
    }

    public static DispatchContext api(int userId, String group, String requestId) {
        return new DispatchContext(DispatchSource.API, userId, group, requestId);
    }

    public static DispatchContext playground(int userId, String group, String requestId) {
        return new DispatchContext(DispatchSource.PLAYGROUND, userId, group, requestId);
    }

    public static DispatchContext agent(int userId, String group, String requestId) {
        return new DispatchContext(DispatchSource.AGENT, userId, group, requestId);
    }

    public static DispatchContext ragIndex(int userId, String group, String requestId) {
        return new DispatchContext(DispatchSource.RAG_INDEX, userId, group, requestId);
    }

    public static DispatchContext ragQuery(int userId, String group, String requestId) {
        return new DispatchContext(DispatchSource.RAG_QUERY, userId, group, requestId);
    }

    public DispatchContext tokenId(Integer tokenId) {
        this.tokenId = tokenId;
        return this;
    }

    public DispatchContext sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public DispatchContext runId(String runId) {
        this.runId = runId;
        return this;
    }
}
