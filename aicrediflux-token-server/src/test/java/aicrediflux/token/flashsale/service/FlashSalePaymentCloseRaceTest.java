package aicrediflux.token.flashsale.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;
import aicrediflux.token.flashsale.mapper.FlashSaleCampaignMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleOutboxMapper;
import aicrediflux.token.flashsale.mapper.TokenPackageMapper;

class FlashSalePaymentCloseRaceTest {
    @Test
    void paymentAndCloseCompetitionProducesExactlyOneLegalTerminalState() throws Exception {
        Instant expiresAt = Instant.parse("2026-09-07T03:00:00Z");
        Clock paymentClock = Clock.fixed(expiresAt.minusMillis(1), ZoneOffset.UTC);
        Clock closeClock = Clock.fixed(expiresAt.plusMillis(1), ZoneOffset.UTC);
        AtomicReference<String> state = new AtomicReference<>("CREATED");
        CyclicBarrier bothSelected = new CyclicBarrier(2);
        FlashSaleOrderMapper orders = mock(FlashSaleOrderMapper.class);
        FlashSaleCampaignMapper campaigns = mock(FlashSaleCampaignMapper.class);
        FlashSaleOutboxMapper outbox = mock(FlashSaleOutboxMapper.class);
        FlashSaleReservationStore reservations = mock(FlashSaleReservationStore.class);
        when(orders.selectOwnedForUpdate("ORDER-1", 3)).thenAnswer(ignored -> {
            bothSelected.await(5, TimeUnit.SECONDS);
            return order(expiresAt);
        });
        when(orders.selectByOrderNoForUpdate("ORDER-1")).thenAnswer(ignored -> {
            bothSelected.await(5, TimeUnit.SECONDS);
            return order(expiresAt);
        });
        when(orders.markPaid("ORDER-1", 3, paymentClock.instant()))
                .thenAnswer(ignored -> state.compareAndSet("CREATED", "PAID") ? 1 : 0);
        when(orders.markClosed("ORDER-1", closeClock.instant()))
                .thenAnswer(ignored -> state.compareAndSet("CREATED", "CLOSED") ? 1 : 0);
        when(outbox.insert(any(FlashSaleOutbox.class))).thenReturn(1);
        when(campaigns.restoreStock(8L)).thenReturn(1);
        FlashSaleCampaign campaign = new FlashSaleCampaign();
        campaign.setId(8L);
        campaign.setAvailableStock(10);
        when(campaigns.selectById(8L)).thenReturn(campaign);
        FlashSaleOrderService payment = new FlashSaleOrderService(
                mock(TokenPackageMapper.class), orders, outbox, paymentClock);
        FlashSaleOrderCloseService close = new FlashSaleOrderCloseService(
                orders, campaigns, reservations, closeClock);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> paid = executor.submit(() -> {
                try {
                    payment.mockPay(3, "ORDER-1");
                    return true;
                } catch (IllegalStateException lostRace) {
                    return false;
                }
            });
            Future<Boolean> closed = executor.submit(() -> close.closeExpired("ORDER-1"));

            boolean paymentWon = paid.get(5, TimeUnit.SECONDS);
            boolean closeWon = closed.get(5, TimeUnit.SECONDS);
            assertTrue(paymentWon ^ closeWon);
            assertEquals(paymentWon ? "PAID" : "CLOSED", state.get());
            if (paymentWon) {
                verify(outbox).insert(any(FlashSaleOutbox.class));
                verify(campaigns, never()).restoreStock(8L);
            } else {
                verify(outbox, never()).insert(any(FlashSaleOutbox.class));
                verify(campaigns).restoreStock(8L);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private FlashSaleOrder order(Instant expiresAt) {
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo("ORDER-1");
        order.setUserId(3);
        order.setCampaignId(8L);
        order.setCreditAmount(10_000L);
        order.setOrderStatus("CREATED");
        order.setPayStatus("UNPAID");
        order.setExpireTime(expiresAt);
        return order;
    }
}
