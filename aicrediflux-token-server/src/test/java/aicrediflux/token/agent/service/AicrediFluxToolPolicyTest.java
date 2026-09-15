package aicrediflux.token.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import aicrediflux.token.mapper.UserMapper;
import aicrediflux.token.pojo.entity.User;

class AicrediFluxToolPolicyTest {
    private final UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);
    private final AicrediFluxToolPolicy policy = new AicrediFluxToolPolicy(userMapper);

    @Test
    void documentQuestionDoesNotExposeChannelStatus() {
        AicrediFluxToolInvocationContext context = new AicrediFluxToolInvocationContext(7, "default", "AS001", "AR001");

        assertThat(policy.isAllowed("channelStatus", context, "no available channel 一般怎么根据文档排查？")).isFalse();
        assertThat(policy.isAllowed("walletBalance", context, "我还有多少 AI Credit？")).isTrue();
    }

    @Test
    void adminLiveChannelQuestionCanUseChannelStatus() {
        User user = new User();
        user.setRole(3);
        when(userMapper.selectById(1)).thenReturn(user);
        AicrediFluxToolInvocationContext context = new AicrediFluxToolInvocationContext(1, "default", "AS001", "AR001");

        assertThat(policy.isAllowed("channelStatus", context, "当前渠道状态、成功率和 429 风险怎么样？")).isTrue();
    }

    @Test
    void ordinaryUserExplicitChannelQuestionCanReachPermissionEnvelope() {
        User user = new User();
        user.setRole(1);
        when(userMapper.selectById(7)).thenReturn(user);
        AicrediFluxToolInvocationContext context = new AicrediFluxToolInvocationContext(7, "default", "AS001", "AR001");

        assertThat(policy.isAllowed("channelStatus", context, "当前渠道状态和延迟怎么样？")).isTrue();
    }
}

