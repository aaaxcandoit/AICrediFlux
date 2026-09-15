package aicrediflux.token.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import aicrediflux.token.mapper.UserMapper;
import aicrediflux.token.pojo.entity.User;

class AicrediFluxCopilotToolsTest {
    private final AgentToolService toolService = org.mockito.Mockito.mock(AgentToolService.class);
    private final UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);
    private final AicrediFluxToolInvocationContextHolder contextHolder = new AicrediFluxToolInvocationContextHolder();
    private final AicrediFluxCopilotTools tools = new AicrediFluxCopilotTools(toolService, userMapper, contextHolder);

    @AfterEach
    void clearContext() {
        contextHolder.clear();
    }

    @Test
    void walletBalanceUsesCurrentUserFromServerContext() {
        contextHolder.set(new AicrediFluxToolInvocationContext(7, "default", "AS001", "AR001"));
        when(toolService.walletBalance(7)).thenReturn(new AgentToolService.WalletBalanceResult(128000L));

        AgentToolService.WalletBalanceResult result = tools.walletBalance();

        assertThat(result.balance()).isEqualTo(128000L);
        verify(toolService).walletBalance(7);
    }

    @Test
    void modelPriceUsesCurrentGroupFromServerContext() {
        contextHolder.set(new AicrediFluxToolInvocationContext(7, "vip", "AS001", "AR001"));
        AgentToolService.ModelPriceResult price = new AgentToolService.ModelPriceResult(
                "deepseek-chat", "vip", true, 0, 1.0, 2.0, 0.0, 0.8,
                Map.of("vip", 0.8), List.of("vip"), 120L, "ok");
        when(toolService.modelPrice("deepseek-chat", "vip", 100, 20)).thenReturn(price);

        assertThat(tools.modelPrice("deepseek-chat", 100, 20)).isSameAs(price);
    }

    @Test
    void modelPriceFallsBackToRunModelWhenArgumentIsBlank() {
        contextHolder.set(new AicrediFluxToolInvocationContext(7, "default", "AS001", "AR001", "deepseek-v4-flash"));
        AgentToolService.ModelPriceResult price = new AgentToolService.ModelPriceResult(
                "deepseek-v4-flash", "default", true, 0, 1.0, 2.0, 0.0, 1.0,
                Map.of("default", 1.0), List.of("default"), 0L, "ok");
        when(toolService.modelPrice("deepseek-v4-flash", "default", 0, 0)).thenReturn(price);

        assertThat(tools.modelPrice("  ", null, null)).isSameAs(price);
    }

    @Test
    void usageSummaryClampsHoursAtToolBoundary() {
        contextHolder.set(new AicrediFluxToolInvocationContext(7, "default", "AS001", "AR001"));
        AgentToolService.UsageSummaryResult usage = new AgentToolService.UsageSummaryResult(744, 0, 0, 0, 0, List.of());
        when(toolService.usageSummary(7, 744, null, null)).thenReturn(usage);

        assertThat(tools.usageSummary(9999, null, null)).isSameAs(usage);
    }

    @Test
    void ordinaryUserCannotUseChannelStatus() {
        contextHolder.set(new AicrediFluxToolInvocationContext(7, "default", "AS001", "AR001"));
        User user = new User();
        user.setRole(1);
        when(userMapper.selectById(7)).thenReturn(user);
        when(toolService.channelStatus(eq(1), eq("deepseek-chat"), eq(24)))
                .thenThrow(new IllegalStateException("ChannelStatusTool 仅管理员可用"));

        assertThrows(IllegalStateException.class, () -> tools.channelStatus("deepseek-chat", 24));
    }

    @Test
    void adminCanUseChannelStatusWithoutSecretOutput() {
        contextHolder.set(new AicrediFluxToolInvocationContext(1, "default", "AS001", "AR001"));
        User user = new User();
        user.setRole(3);
        when(userMapper.selectById(1)).thenReturn(user);
        AgentToolService.ChannelStatusResult status = new AgentToolService.ChannelStatusResult(24, List.of());
        when(toolService.channelStatus(3, "deepseek-chat", 24)).thenReturn(status);

        assertThat(tools.channelStatus("deepseek-chat", 24)).isSameAs(status);
    }
}

