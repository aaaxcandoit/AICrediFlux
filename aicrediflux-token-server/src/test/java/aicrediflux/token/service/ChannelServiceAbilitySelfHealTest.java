package aicrediflux.token.service;

import aicrediflux.token.mapper.AbilityMapper;
import aicrediflux.token.mapper.ChannelMapper;
import aicrediflux.token.pojo.entity.Ability;
import aicrediflux.token.pojo.entity.Channel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ChannelService abilities 自修复")
class ChannelServiceAbilitySelfHealTest {

    @Test
    @DisplayName("当渠道 models/group 已包含模型但 abilities 缺失时，选渠前自动补齐 ability")
    void repairsMissingAbilitiesFromEnabledChannelModels() {
        ChannelMapper channelMapper = mock(ChannelMapper.class);
        AbilityMapper abilityMapper = mock(AbilityMapper.class);
        ChannelService service = new ChannelService(channelMapper, abilityMapper);

        String model = "Qwen/Qwen3-Embedding-0.6B";
        Channel siliconFlow = new Channel();
        siliconFlow.setId(3);
        siliconFlow.setName("SiliconFlow");
        siliconFlow.setStatus(1);
        siliconFlow.setGroup("default");
        siliconFlow.setModels("deepseek-chat," + model);
        siliconFlow.setPriority(7L);
        siliconFlow.setWeight(3);
        siliconFlow.setTag("embedding");

        Ability repaired = new Ability();
        repaired.setGroup("default");
        repaired.setModel(model);
        repaired.setChannelId(3);
        repaired.setEnabled(true);
        repaired.setPriority(7L);
        repaired.setWeight(3);
        repaired.setTag("embedding");

        when(abilityMapper.selectEnabledAbilities("default", model))
                .thenReturn(List.of())
                .thenReturn(List.of(repaired));
        when(channelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(siliconFlow));
        when(abilityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(channelMapper.selectById(3)).thenReturn(siliconFlow);

        Channel selected = service.getRandomSatisfiedChannel("default", model, 0, null);

        assertNotNull(selected);
        assertEquals(3, selected.getId());
        verify(abilityMapper).insert(any(Ability.class));
        verify(abilityMapper, times(2)).selectEnabledAbilities(eq("default"), eq(model));
    }
}
