package aicrediflux.token.agent.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import aicrediflux.token.config.ratio.GroupRatioConfig;
import aicrediflux.token.integration.credit.CreditAccountFacade;
import aicrediflux.token.mapper.ChannelMapper;
import aicrediflux.token.mapper.LogMapper;
import aicrediflux.token.pojo.entity.Channel;
import aicrediflux.token.pojo.entity.Log;
import aicrediflux.token.pojo.vo.PricingVO;
import aicrediflux.token.service.PricingService;

@Service
public class AgentToolService {
    private static final int MAX_USAGE_ROWS = 1000;
    private static final int MAX_CHANNEL_LOG_ROWS = 2000;

    private final CreditAccountFacade creditAccount;
    private final PricingService pricingService;
    private final LogMapper logMapper;
    private final ChannelMapper channelMapper;

    public AgentToolService(CreditAccountFacade creditAccount, PricingService pricingService,
                            LogMapper logMapper, ChannelMapper channelMapper) {
        this.creditAccount = creditAccount;
        this.pricingService = pricingService;
        this.logMapper = logMapper;
        this.channelMapper = channelMapper;
    }

    public WalletBalanceResult walletBalance(int userId) {
        return new WalletBalanceResult(creditAccount.getBalance(userId));
    }

    public ModelPriceResult modelPrice(String modelName, String group, int promptTokens, int completionTokens) {
        String normalizedGroup = group == null || group.isBlank() ? "default" : group.trim();
        PricingVO pricing = pricingService.getPricing().stream()
                .filter(item -> Objects.equals(item.getModelName(), modelName))
                .findFirst()
                .orElse(null);
        if (pricing == null) {
            return new ModelPriceResult(modelName, normalizedGroup, false, 0, 0D, 0D, 0D, 1D,
                    GroupRatioConfig.getGroupRatioCopy(), List.<String>of(), 0L,
                    "模型尚未出现在平台定价快照中，请先检查模型定价和渠道能力配置");
        }
        double groupRatio = GroupRatioConfig.getGroupRatio(normalizedGroup);
        long estimated = estimateCost(pricing, groupRatio, promptTokens, completionTokens);
        return new ModelPriceResult(
                pricing.getModelName(),
                normalizedGroup,
                true,
                pricing.getQuotaType(),
                pricing.getModelRatio(),
                pricing.getCompletionRatio(),
                pricing.getModelPrice(),
                groupRatio,
                GroupRatioConfig.getGroupRatioCopy(),
                pricing.getEnableGroup() == null ? List.of() : pricing.getEnableGroup(),
                estimated,
                pricing.getQuotaType() == 1
                        ? "按模型价格估算，实际扣费以 Billing 日志为准"
                        : "按 input*模型倍率 + output*模型倍率*补全倍率，再乘分组倍率估算，实际扣费以 Billing 日志为准");
    }

    public UsageSummaryResult usageSummary(int userId, Integer hours, String modelName, Integer tokenId) {
        int safeHours = hours == null || hours <= 0 ? 24 : Math.min(hours, 24 * 31);
        long start = System.currentTimeMillis() / 1000 - safeHours * 3600L;
        QueryWrapper<Log> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .ge("created_at", start)
                .orderByDesc("id")
                .last("LIMIT " + MAX_USAGE_ROWS);
        if (modelName != null && !modelName.isBlank()) {
            query.eq("model_name", modelName.trim());
        }
        if (tokenId != null && tokenId > 0) {
            query.eq("token_id", tokenId);
        }
        List<Log> logs = logMapper.selectList(query);
        long totalQuota = logs.stream().map(Log::getQuota).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long prompt = logs.stream().map(Log::getPromptTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long completion = logs.stream().map(Log::getCompletionTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        Map<String, List<Log>> byModel = logs.stream()
                .collect(Collectors.groupingBy(log -> blankToUnknown(log.getModelName()), LinkedHashMap::new, Collectors.toList()));
        List<UsageModelSummary> models = new ArrayList<>();
        byModel.forEach((name, rows) -> models.add(new UsageModelSummary(name, rows.size(),
                rows.stream().map(Log::getQuota).filter(Objects::nonNull).mapToLong(Integer::longValue).sum(),
                rows.stream().map(Log::getPromptTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum(),
                rows.stream().map(Log::getCompletionTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum())));
        models.sort((a, b) -> Long.compare(b.quota(), a.quota()));
        return new UsageSummaryResult(safeHours, logs.size(), totalQuota, prompt, completion, models);
    }

    public ChannelStatusResult channelStatus(int role, String modelName, Integer hours) {
        if (role < 2) {
            throw new IllegalStateException("ChannelStatusTool 仅管理员可用");
        }
        int safeHours = hours == null || hours <= 0 ? 24 : Math.min(hours, 24 * 31);
        List<Channel> channels = channelMapper.selectList(null).stream()
                .filter(channel -> modelName == null || modelName.isBlank() || containsModel(channel.getModels(), modelName.trim()))
                .toList();
        long start = System.currentTimeMillis() / 1000 - safeHours * 3600L;
        QueryWrapper<Log> query = new QueryWrapper<>();
        query.ge("created_at", start)
                .orderByDesc("id")
                .last("LIMIT " + MAX_CHANNEL_LOG_ROWS);
        if (modelName != null && !modelName.isBlank()) {
            query.eq("model_name", modelName.trim());
        }
        List<Log> recentLogs = logMapper.selectList(query);
        Map<Integer, List<Log>> logsByChannel = recentLogs.stream()
                .filter(log -> log.getChannelId() != null)
                .collect(Collectors.groupingBy(Log::getChannelId));
        List<ChannelSummary> summaries = channels.stream()
                .map(channel -> summarizeChannel(channel, logsByChannel.getOrDefault(channel.getId(), List.of())))
                .toList();
        return new ChannelStatusResult(safeHours, summaries);
    }

    private ChannelSummary summarizeChannel(Channel channel, List<Log> logs) {
        long total = logs.size();
        long errors = logs.stream().filter(this::looksLikeError).count();
        double successRate = total == 0 ? 0 : Math.round((total - errors) * 10000.0 / total) / 100.0;
        long quota = logs.stream().map(Log::getQuota).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long avgLatency = Math.round(logs.stream()
                .map(Log::getUseTime)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .average()
                .orElse(0));
        return new ChannelSummary(channel.getId(), channel.getName(), channel.getStatus(), channel.getGroup(),
                channel.getPriority(), channel.getWeight(), safeModels(channel.getModels()), channel.getUsedQuota(),
                channel.getResponseTime(), total, errors, successRate, avgLatency, quota);
    }

    private long estimateCost(PricingVO pricing, double groupRatio, int promptTokens, int completionTokens) {
        int safePrompt = Math.max(promptTokens, 0);
        int safeCompletion = Math.max(completionTokens, 0);
        double raw;
        if (pricing.getQuotaType() == 1) {
            raw = (safePrompt + safeCompletion) / 1000.0 * Math.max(pricing.getModelPrice(), 0);
        } else {
            raw = (safePrompt * pricing.getModelRatio()
                    + safeCompletion * pricing.getModelRatio() * Math.max(pricing.getCompletionRatio(), 1.0));
        }
        return Math.max(0, Math.round(raw * groupRatio));
    }

    private boolean containsModel(String models, String modelName) {
        if (models == null || models.isBlank()) return false;
        for (String model : models.split(",")) {
            if (model.trim().equals(modelName)) return true;
        }
        return false;
    }

    private boolean looksLikeError(Log log) {
        String content = log.getContent();
        return content != null && content.toLowerCase(Locale.ROOT).contains("error");
    }

    private List<String> safeModels(String models) {
        if (models == null || models.isBlank()) return List.of();
        return List.of(models.split(",")).stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public record WalletBalanceResult(long balance) {}
    public record ModelPriceResult(String modelName, String group, boolean configured, int quotaType,
                                   double modelRatio, double completionRatio, double modelPrice, double groupRatio,
                                   Map<String, Double> allGroupRatios, List<String> enabledGroups,
                                   long estimatedCreditCost, String note) {}
    public record UsageSummaryResult(int hours, long requestCount, long totalQuota, long promptTokens,
                                     long completionTokens, List<UsageModelSummary> models) {}
    public record UsageModelSummary(String modelName, long requestCount, long quota, long promptTokens,
                                    long completionTokens) {}
    public record ChannelStatusResult(int hours, List<ChannelSummary> channels) {}
    public record ChannelSummary(Integer id, String name, Integer status, String group, Long priority,
                                 Integer weight, List<String> models, Long usedQuota, Integer responseTime,
                                 long recentRequests, long recentErrors, double successRate,
                                 long avgLatencyMs, long recentQuota) {}
}


