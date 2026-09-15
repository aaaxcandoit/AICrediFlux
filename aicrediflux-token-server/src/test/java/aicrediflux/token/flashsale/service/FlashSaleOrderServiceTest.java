package aicrediflux.token.flashsale.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;
import aicrediflux.token.flashsale.domain.TokenPackage;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleOutboxMapper;
import aicrediflux.token.flashsale.mapper.TokenPackageMapper;

class FlashSaleOrderServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void ordinaryPurchaseCopiesPackageSnapshotAndExpiresInFifteenMinutes() {
        TokenPackageMapper packageMapper = mock(TokenPackageMapper.class);
        FlashSaleOrderMapper orderMapper = mock(FlashSaleOrderMapper.class);
        FlashSaleOutboxMapper outboxMapper = mock(FlashSaleOutboxMapper.class);
        TokenPackage pack = new TokenPackage();
        pack.setId(7L);
        pack.setName("500K Credit");
        pack.setCreditAmount(500_000L);
        pack.setSalePrice(9_900L);
        pack.setStatus(1);
        when(packageMapper.selectById(7L)).thenReturn(pack);
        when(orderMapper.insert(any(FlashSaleOrder.class))).thenReturn(1);

        FlashSaleOrderService service = new FlashSaleOrderService(packageMapper, orderMapper, outboxMapper, CLOCK);
        FlashSaleOrder order = service.createOrdinaryOrder(12, 7L);

        assertEquals(500_000L, order.getCreditAmount());
        assertEquals(9_900L, order.getPayAmount());
        assertEquals("CREATED", order.getOrderStatus());
        assertEquals(Instant.parse("2026-09-07T03:15:00Z"), order.getExpireTime());
    }

    @Test
    void mockPayCreatesCreditGrantEventExactlyOnce() {
        TokenPackageMapper packageMapper = mock(TokenPackageMapper.class);
        FlashSaleOrderMapper orderMapper = mock(FlashSaleOrderMapper.class);
        FlashSaleOutboxMapper outboxMapper = mock(FlashSaleOutboxMapper.class);
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo("MR202609070001");
        order.setUserId(12);
        order.setCreditAmount(500_000L);
        order.setOrderStatus("CREATED");
        order.setPayStatus("UNPAID");
        order.setExpireTime(Instant.parse("2026-09-07T03:15:00Z"));
        FlashSaleOrder paid = new FlashSaleOrder();
        paid.setOrderNo(order.getOrderNo());
        paid.setUserId(order.getUserId());
        paid.setCreditAmount(order.getCreditAmount());
        paid.setOrderStatus("PAID");
        paid.setPayStatus("PAID");
        paid.setCreditStatus("PENDING");
        paid.setExpireTime(order.getExpireTime());
        when(orderMapper.selectOwnedForUpdate("MR202609070001", 12)).thenReturn(order, paid);
        when(orderMapper.markPaid("MR202609070001", 12, Instant.parse("2026-09-07T03:00:00Z"))).thenReturn(1);
        when(outboxMapper.insert(any(FlashSaleOutbox.class))).thenReturn(1);

        FlashSaleOrderService service = new FlashSaleOrderService(packageMapper, orderMapper, outboxMapper, CLOCK);
        service.mockPay(12, "MR202609070001");
        service.mockPay(12, "MR202609070001");

        verify(outboxMapper, times(1)).insert(org.mockito.ArgumentMatchers.<FlashSaleOutbox>argThat(event ->
                "mr-credit-grant".equals(event.getTopic())
                        && "CREDIT_GRANT".equals(event.getEventType())
                        && event.getPayload().contains("MR202609070001")));
    }

    @Test
    void expiredOrderCannotBePaid() {
        TokenPackageMapper packageMapper = mock(TokenPackageMapper.class);
        FlashSaleOrderMapper orderMapper = mock(FlashSaleOrderMapper.class);
        FlashSaleOutboxMapper outboxMapper = mock(FlashSaleOutboxMapper.class);
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo("MR-EXPIRED");
        order.setUserId(12);
        order.setOrderStatus("CREATED");
        order.setPayStatus("UNPAID");
        order.setExpireTime(Instant.parse("2026-09-07T02:59:59Z"));
        when(orderMapper.selectOwnedForUpdate("MR-EXPIRED", 12)).thenReturn(order);

        FlashSaleOrderService service = new FlashSaleOrderService(packageMapper, orderMapper, outboxMapper, CLOCK);
        assertThrows(IllegalStateException.class, () -> service.mockPay(12, "MR-EXPIRED"));
        verifyNoInteractions(outboxMapper);
    }

    @Test
    void mockPayUsesConfiguredCreditTopic() {
        TokenPackageMapper packageMapper = mock(TokenPackageMapper.class);
        FlashSaleOrderMapper orderMapper = mock(FlashSaleOrderMapper.class);
        FlashSaleOutboxMapper outboxMapper = mock(FlashSaleOutboxMapper.class);
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo("MR-ACCEPTANCE");
        order.setUserId(12);
        order.setCreditAmount(500_000L);
        order.setOrderStatus("CREATED");
        order.setPayStatus("UNPAID");
        order.setExpireTime(Instant.parse("2026-09-07T03:15:00Z"));
        when(orderMapper.selectOwnedForUpdate("MR-ACCEPTANCE", 12)).thenReturn(order);
        when(orderMapper.markPaid("MR-ACCEPTANCE", 12, CLOCK.instant())).thenReturn(1);
        when(outboxMapper.insert(any(FlashSaleOutbox.class))).thenReturn(1);
        FlashSaleProperties properties = new FlashSaleProperties();
        properties.getTopics().setCredit("mr-credit-grant-acceptance");

        new FlashSaleOrderService(packageMapper, orderMapper, outboxMapper, properties, CLOCK)
                .mockPay(12, "MR-ACCEPTANCE");

        verify(outboxMapper).insert(org.mockito.ArgumentMatchers.<FlashSaleOutbox>argThat(event ->
                "mr-credit-grant-acceptance".equals(event.getTopic())));
    }
}
