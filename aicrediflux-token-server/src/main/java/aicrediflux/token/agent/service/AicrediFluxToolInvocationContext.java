package aicrediflux.token.agent.service;

public record AicrediFluxToolInvocationContext(int userId, String group, String sessionNo, String runNo, String modelName) {
    public AicrediFluxToolInvocationContext(int userId, String group, String sessionNo, String runNo) {
        this(userId, group, sessionNo, runNo, "");
    }
}
