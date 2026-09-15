package aicrediflux.token.flashsale.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.TokenPackage;
import aicrediflux.token.flashsale.mapper.FlashSaleCampaignMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleRequestMapper;
import aicrediflux.token.flashsale.mapper.TokenPackageMapper;

class FlashSaleCampaignServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void publishingWarmsRedisWithoutChangingSnapshotsLater() {
        FlashSaleCampaignMapper mapper = mock(FlashSaleCampaignMapper.class);
        TokenPackageMapper packageMapper = mock(TokenPackageMapper.class);
        FlashSaleRequestMapper requestMapper = mock(FlashSaleRequestMapper.class);
        FlashSaleReservationStore store = mock(FlashSaleReservationStore.class);
        FlashSaleCampaign campaign = campaign();
        TokenPackage pack = new TokenPackage();
        pack.setId(7L);
        when(mapper.selectById(8L)).thenReturn(campaign);
        when(packageMapper.selectById(7L)).thenReturn(pack);
        when(mapper.publish(8L)).thenReturn(1);
        FlashSaleCampaignService service = new FlashSaleCampaignService(
                mapper, packageMapper, requestMapper, store, CLOCK);

        service.publish(8L);

        verify(store).warmup(campaign);
        campaign.setStatus(1);
        FlashSaleCampaign changed = campaign();
        changed.setFlashPrice(1L);
        assertThrows(IllegalStateException.class, () -> service.update(8L, changed));
    }

    private FlashSaleCampaign campaign() {
        FlashSaleCampaign campaign = new FlashSaleCampaign();
        campaign.setId(8L);
        campaign.setPackageId(7L);
        campaign.setActivityName("活动");
        campaign.setFlashPrice(9900L);
        campaign.setCreditAmount(500000L);
        campaign.setTotalStock(100);
        campaign.setAvailableStock(100);
        campaign.setPerUserLimit(1);
        campaign.setStartTime(Instant.parse("2026-09-07T04:00:00Z"));
        campaign.setEndTime(Instant.parse("2026-09-07T05:00:00Z"));
        campaign.setStatus(0);
        return campaign;
    }
}