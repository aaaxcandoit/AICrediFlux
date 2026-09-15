package aicrediflux.token.agent.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import aicrediflux.token.mapper.UserMapper;
import aicrediflux.token.pojo.entity.User;

@Component
public class AicrediFluxCopilotTools {
    private final AgentToolService toolService;
    private final UserMapper userMapper;
    private final AicrediFluxToolInvocationContextHolder contextHolder;

    public AicrediFluxCopilotTools(AgentToolService toolService, UserMapper userMapper,
                                   AicrediFluxToolInvocationContextHolder contextHolder) {
        this.toolService = toolService;
        this.userMapper = userMapper;
        this.contextHolder = contextHolder;
    }

    @Tool(name = "walletBalance", description = "查询当前登录用户的 AI Credit 钱包余额，只能查询自己")
    public AgentToolService.WalletBalanceResult walletBalance() {
        AicrediFluxToolInvocationContext context = contextHolder.required();
        return toolService.walletBalance(context.userId());
    }

    @Tool(name = "modelPrice", description = "查询平台模型价格、模型倍率、补全倍率、分组倍率，并按给定 token 数估算 AI Credit 消耗")
    public AgentToolService.ModelPriceResult modelPrice(
            @ToolParam(description = "模型名称，例如 deepseek-chat 或 qwen-plus") String modelName,
            @ToolParam(description = "预估输入 token 数", required = false) Integer promptTokens,
            @ToolParam(description = "预估输出 token 数", required = false) Integer completionTokens) {
        AicrediFluxToolInvocationContext context = contextHolder.required();
        return toolService.modelPrice(defaultModel(modelName, context), context.group(), value(promptTokens), value(completionTokens));
    }

    @Tool(name = "usageSummary", description = "查询当前登录用户自己的 API/Playground/Agent 调用用量汇总，可按时间、模型和 API Key 聚合")
    public AgentToolService.UsageSummaryResult usageSummary(
            @ToolParam(description = "最近多少小时，默认 24，最大 744", required = false) Integer hours,
            @ToolParam(description = "模型名称过滤", required = false) String modelName,
            @ToolParam(description = "API Token ID 过滤，只能用于当前登录用户自己的 token", required = false) Integer tokenId) {
        AicrediFluxToolInvocationContext context = contextHolder.required();
        return toolService.usageSummary(context.userId(), clampHours(hours), modelName, tokenId);
    }

    @Tool(name = "channelStatus", description = "管理员只读查询渠道状态、可用模型、近期错误、成功率和延迟，不暴露上游 Key，也不修改配置")
    public AgentToolService.ChannelStatusResult channelStatus(
            @ToolParam(description = "模型名称过滤", required = false) String modelName,
            @ToolParam(description = "最近多少小时，默认 24，最大 744", required = false) Integer hours) {
        AicrediFluxToolInvocationContext context = contextHolder.required();
        User user = userMapper.selectById(context.userId());
        int role = user == null || user.getRole() == null ? 1 : user.getRole();
        return toolService.channelStatus(role, modelName, clampHours(hours));
    }

    private int value(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private int clampHours(Integer hours) {
        if (hours == null || hours <= 0) {
            return 24;
        }
        return Math.min(hours, 24 * 31);
    }

    private String defaultModel(String modelName, AicrediFluxToolInvocationContext context) {
        if (modelName != null && !modelName.isBlank()) {
            return modelName.trim();
        }
        return context.modelName() == null ? "" : context.modelName().trim();
    }
}

