package aicrediflux.token.agent.service;

import org.springframework.stereotype.Component;

@Component
public class AicrediFluxChatUsageHolder {
    private final ThreadLocal<AicrediFluxChatUsage> current = ThreadLocal.withInitial(AicrediFluxChatUsage::empty);

    public void clear() {
        current.remove();
    }

    public void record(AicrediFluxChatUsage usage) {
        current.set(usage == null ? AicrediFluxChatUsage.empty() : usage);
    }

    public AicrediFluxChatUsage current() {
        return current.get();
    }
}
