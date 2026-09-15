package aicrediflux.token.agent.service;

public record AicrediFluxChatUsage(int promptTokens, int completionTokens, long quota,
                                   String requestId, Integer channelId) {
    public static AicrediFluxChatUsage empty() {
        return new AicrediFluxChatUsage(0, 0, 0L, null, null);
    }

    public AicrediFluxChatUsage plus(InternalChatResult result) {
        if (result == null) {
            return this;
        }
        return new AicrediFluxChatUsage(
                promptTokens + result.promptTokens(),
                completionTokens + result.completionTokens(),
                quota + result.quota(),
                result.requestId() == null ? requestId : result.requestId(),
                result.channelId() == null ? channelId : result.channelId());
    }
}
