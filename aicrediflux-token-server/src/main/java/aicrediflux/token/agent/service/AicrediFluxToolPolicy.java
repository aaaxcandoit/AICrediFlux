package aicrediflux.token.agent.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import aicrediflux.token.mapper.UserMapper;
import aicrediflux.token.pojo.entity.User;

@Component
public class AicrediFluxToolPolicy {
    private final UserMapper userMapper;

    public AicrediFluxToolPolicy(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public boolean isAllowed(String toolName, AicrediFluxToolInvocationContext context, String latestQuestion) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String question = normalize(latestQuestion);
        return switch (toolName) {
            case "walletBalance" -> mentionsAny(question, "余额", "钱包", "还剩", "还能用", "还能用多久", "健康", "ai credit", "credit");
            case "usageSummary" -> mentionsAny(question, "用量", "消耗", "调用统计", "统计", "使用", "使用习惯", "这周", "7 天", "七天", "请求次数", "余额健康", "还能用多久", "api key", "api token", "token");
            case "modelPrice" -> mentionsAny(question, "价格", "成本", "倍率", "费用", "估算", "模型收费", "model price") || (mentionsAny(question, "no available channel", "风险") && mentionsAny(question, "deepseek", "qwen", "模型"));
            case "channelStatus" -> looksLikeLiveChannelDiagnostic(question) && !looksLikeDocumentQuestion(question);
            default -> false;
        };
    }

    private boolean isAdmin(AicrediFluxToolInvocationContext context) {
        if (context == null || context.userId() <= 0) {
            return false;
        }
        User user = userMapper.selectById(context.userId());
        return user != null && user.getRole() != null && user.getRole() >= 2;
    }

    private boolean looksLikeLiveChannelDiagnostic(String question) {
        return mentionsAny(question, "当前渠道", "渠道状态", "最近渠道", "channel status", "成功率", "延迟",
                "429", "5xx", "实时可用", "实时可用性", "是否正常", "可用性", "风险");
    }

    private boolean looksLikeDocumentQuestion(String question) {
        return mentionsAny(question, "文档", "说明", "指南", "怎么配置", "如何配置", "配置方法", "错误码解释",
                "一般怎么", "通常", "排查顺序", "知识库", "rag");
    }

    private boolean mentionsAny(String question, String... keywords) {
        if (question == null || question.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (question.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}

