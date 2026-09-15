package aicrediflux.token.agent.service;

import org.springframework.stereotype.Component;

@Component
public class AicrediFluxToolInvocationContextHolder {
    private final ThreadLocal<AicrediFluxToolInvocationContext> current = new ThreadLocal<>();

    public void set(AicrediFluxToolInvocationContext context) {
        current.set(context);
    }

    public AicrediFluxToolInvocationContext required() {
        AicrediFluxToolInvocationContext context = current.get();
        if (context == null) {
            throw new IllegalStateException("Copilot 工具缺少运行上下文");
        }
        return context;
    }

    public void clear() {
        current.remove();
    }
}
