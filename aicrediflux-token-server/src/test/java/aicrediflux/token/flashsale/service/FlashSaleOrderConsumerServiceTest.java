package aicrediflux.token.flashsale.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;
import aicrediflux.token.flashsale.domain.FlashSaleRequest;
import aicrediflux.token.flashsale.domain.TokenPackage;
import aicrediflux.token.flashsale.mapper.*;

class FlashSaleOrderConsumerServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsOneOrderAndSchedulesTimeoutClose() {
        FlashSaleRequestMapper requests = mock(FlashSaleRequestMapper.class);
        FlashSaleCampaignMapper campaigns = mock(FlashSaleCampaignMapper.class);
        TokenPackageMapper packages = mock(TokenPackageMapper.class);
        FlashSaleOrderMapper orders = mock(FlashSaleOrderMapper.class);
        FlashSaleOutboxMapper outbox = mock(FlashSaleOutboxMapper.class);
        FlashSaleRequest request = request("PENDING");
        FlashSaleCampaign campaign = campaign();
        TokenPackage pack = new TokenPackage();
        pack.setId(7L);
        pack.setName("Flash Pack");
        when(requests.selectForUpdate("REQ-1")).thenReturn(request);
        when(campaigns.selectById(8L)).thenReturn(campaign);
        when(packages.selectById(7L)).thenReturn(pack);
        when(campaigns.decreaseStock(8L)).thenReturn(1);
        when(orders.insert(any(FlashSaleOrder.class))).thenReturn(1);
        when(requests.markProcessed("REQ-1")).thenReturn(1);
        when(outbox.insert(any(FlashSaleOutbox.class))).thenReturn(1);
        FlashSaleOrderConsumerService service = new FlashSaleOrderConsumerService(
                requests, campaigns, packages, orders, outbox, CLOCK);

        FlashSaleOrder result = service.createOrder(new FlashSaleService.OrderCreateMessage("REQ-1", "ORDER-1", 8L, 3));

        assertEquals("ORDER-1", result.getOrderNo());
        assertEquals(900L, result.getPayAmount());
        assertEquals(10_000L, result.getCreditAmount());
        verify(outbox).insert(org.mockito.ArgumentMatchers.<FlashSaleOutbox>argThat(event -> "ORDER_CLOSE".equals(event.getEventType())
                && Instant.parse("2026-09-07T03:15:00Z").equals(event.getDeliverAt())));
    }

    @Test
    void duplicateMessageDoesNotDeductStockAgain() {
        FlashSaleRequestMapper requests = mock(FlashSaleRequestMapper.class);
        FlashSaleCampaignMapper campaigns = mock(FlashSaleCampaignMapper.class);
        TokenPackageMapper packages = mock(TokenPackageMapper.class);
        FlashSaleOrderMapper orders = mock(FlashSaleOrderMapper.class);
        FlashSaleOutboxMapper outbox = mock(FlashSaleOutboxMapper.class);
        when(requests.selectForUpdate("REQ-1")).thenReturn(request("PROCESSED"));
        FlashSaleOrder existing = new FlashSaleOrder();
        existing.setOrderNo("ORDER-1");
        when(orders.selectByOrderNo("ORDER-1")).thenReturn(existing);
        FlashSaleOrderConsumerService service = new FlashSaleOrderConsumerService(
                requests, campaigns, packages, orders, outbox, CLOCK);

        assertEquals("ORDER-1", service.createOrder(
                new FlashSaleService.OrderCreateMessage("REQ-1", "ORDER-1", 8L, 3)).getOrderNo());
        verifyNoInteractions(campaigns, packages, outbox);
    }

    @Test
    void schedulesCloseOnConfiguredTopic() {
        FlashSaleRequestMapper requests = mock(FlashSaleRequestMapper.class);
        FlashSaleCampaignMapper campaigns = mock(FlashSaleCampaignMapper.class);
        TokenPackageMapper packages = mock(TokenPackageMapper.class);
        FlashSaleOrderMapper orders = mock(FlashSaleOrderMapper.class);
        FlashSaleOutboxMapper outbox = mock(FlashSaleOutboxMapper.class);
        when(requests.selectForUpdate("REQ-1")).thenReturn(request("PENDING"));
        when(campaigns.selectById(8L)).thenReturn(campaign());
        TokenPackage pack = new TokenPackage();
        pack.setId(7L);
        pack.setName("Flash Pack");
        when(packages.selectById(7L)).thenReturn(pack);
        when(campaigns.decreaseStock(8L)).thenReturn(1);
        when(orders.insert(any(FlashSaleOrder.class))).thenReturn(1);
        when(requests.markProcessed("REQ-1")).thenReturn(1);
        when(outbox.insert(any(FlashSaleOutbox.class))).thenReturn(1);
        FlashSaleProperties properties = new FlashSaleProperties();
        properties.getTopics().setClose("mr-flash-sale-delay-close-acceptance");

        new FlashSaleOrderConsumerService(requests, campaigns, packages, orders, outbox, properties, CLOCK)
                .createOrder(new FlashSaleService.OrderCreateMessage("REQ-1", "ORDER-1", 8L, 3));

        verify(outbox).insert(org.mockito.ArgumentMatchers.<FlashSaleOutbox>argThat(event ->
                "mr-flash-sale-delay-close-acceptance".equals(event.getTopic())));
    }

    private FlashSaleRequest request(String status) {
        FlashSaleRequest request = new FlashSaleRequest();
        request.setRequestNo("REQ-1");
        request.setOrderNo("ORDER-1");
        request.setCampaignId(8L);
        request.setUserId(3);
        request.setProcessStatus(status);
        return request;
    }

    private FlashSaleCampaign campaign() {
        FlashSaleCampaign campaign = new FlashSaleCampaign();
        campaign.setId(8L);
        campaign.setPackageId(7L);
        campaign.setActivityName("Launch");
        campaign.setFlashPrice(900L);
        campaign.setCreditAmount(10_000L);
        campaign.setStatus(1);
        campaign.setStartTime(Instant.parse("2026-09-07T02:00:00Z"));
        campaign.setEndTime(Instant.parse("2026-09-07T04:00:00Z"));
        return campaign;
    }
}
