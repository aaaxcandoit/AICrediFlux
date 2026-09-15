package aicrediflux.token.flashsale.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.mapper.FlashSaleCampaignMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;

class FlashSaleOrderCloseServiceTest {
    @Test
    void closesExpiredOrderAndRestoresStockWithoutReleasingUserQualification() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T03:20:00Z"), ZoneOffset.UTC);
        FlashSaleOrderMapper orders = mock(FlashSaleOrderMapper.class);
        FlashSaleCampaignMapper campaigns = mock(FlashSaleCampaignMapper.class);
        FlashSaleReservationStore store = mock(FlashSaleReservationStore.class);
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo("ORDER-1");
        order.setCampaignId(8L);
        order.setOrderStatus("CREATED");
        order.setPayStatus("UNPAID");
        order.setExpireTime(Instant.parse("2026-09-07T03:15:00Z"));
        FlashSaleCampaign campaign = new FlashSaleCampaign();
        campaign.setId(8L);
        campaign.setAvailableStock(5);
        when(orders.selectByOrderNoForUpdate("ORDER-1")).thenReturn(order);
        when(orders.markClosed("ORDER-1", clock.instant())).thenReturn(1);
        when(campaigns.restoreStock(8L)).thenReturn(1);
        when(campaigns.selectById(8L)).thenReturn(campaign);
        FlashSaleOrderCloseService service = new FlashSaleOrderCloseService(orders, campaigns, store, clock);

        assertTrue(service.closeExpired("ORDER-1"));

        verify(store).synchronizeStock(8L, 5);
        verify(store, never()).rollback(anyLong(), anyInt(), anyString());
    }
}
