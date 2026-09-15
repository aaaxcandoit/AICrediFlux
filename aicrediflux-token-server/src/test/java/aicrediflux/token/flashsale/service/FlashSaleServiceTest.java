package aicrediflux.token.flashsale.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;
import aicrediflux.token.flashsale.domain.FlashSaleRequest;
import aicrediflux.token.flashsale.mapper.FlashSaleOutboxMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleRequestMapper;

class FlashSaleServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptedReservationCreatesPendingRequestAndOutboxEvent() {
        FlashSaleReservationStore store = mock(FlashSaleReservationStore.class);
        FlashSaleRequestMapper requestMapper = mock(FlashSaleRequestMapper.class);
        FlashSaleOutboxMapper outboxMapper = mock(FlashSaleOutboxMapper.class);
        when(store.reserve(eq(8L), eq(3), anyString(), eq(CLOCK.instant())))
                .thenReturn(FlashSaleReservationResult.SUCCESS);
        when(requestMapper.insert(any(FlashSaleRequest.class))).thenReturn(1);
        when(outboxMapper.insert(any(FlashSaleOutbox.class))).thenReturn(1);

        FlashSaleService service = new FlashSaleService(store, requestMapper, outboxMapper, CLOCK);
        FlashSaleSubmission result = service.submit(3, 8L);

        assertEquals("PENDING", result.status());
        assertNotNull(result.orderNo());
        verify(store, never()).rollback(anyLong(), anyInt(), anyString());
    }

    @Test
    void databaseFailureRollsBackRedisReservation() {
        FlashSaleReservationStore store = mock(FlashSaleReservationStore.class);
        FlashSaleRequestMapper requestMapper = mock(FlashSaleRequestMapper.class);
        FlashSaleOutboxMapper outboxMapper = mock(FlashSaleOutboxMapper.class);
        when(store.reserve(eq(8L), eq(3), anyString(), eq(CLOCK.instant())))
                .thenReturn(FlashSaleReservationResult.SUCCESS);
        when(requestMapper.insert(any(FlashSaleRequest.class))).thenThrow(new IllegalStateException("db down"));

        FlashSaleService service = new FlashSaleService(store, requestMapper, outboxMapper, CLOCK);
        assertThrows(IllegalStateException.class, () -> service.submit(3, 8L));
        verify(store).rollback(eq(8L), eq(3), anyString());
    }

    @Test
    void duplicateReservationDoesNotCreateDatabaseRequest() {
        FlashSaleReservationStore store = mock(FlashSaleReservationStore.class);
        FlashSaleRequestMapper requestMapper = mock(FlashSaleRequestMapper.class);
        FlashSaleOutboxMapper outboxMapper = mock(FlashSaleOutboxMapper.class);
        when(store.reserve(eq(8L), eq(3), anyString(), eq(CLOCK.instant())))
                .thenReturn(FlashSaleReservationResult.DUPLICATE);

        FlashSaleService service = new FlashSaleService(store, requestMapper, outboxMapper, CLOCK);
        FlashSaleRejectedException error = assertThrows(FlashSaleRejectedException.class, () -> service.submit(3, 8L));
        assertEquals("每个活动只能购买一次", error.getMessage());
        verifyNoInteractions(requestMapper, outboxMapper);
    }

    @Test
    void acceptedReservationUsesConfiguredOrderTopic() {
        FlashSaleReservationStore store = mock(FlashSaleReservationStore.class);
        FlashSaleRequestMapper requestMapper = mock(FlashSaleRequestMapper.class);
        FlashSaleOutboxMapper outboxMapper = mock(FlashSaleOutboxMapper.class);
        when(store.reserve(eq(8L), eq(3), anyString(), eq(CLOCK.instant())))
                .thenReturn(FlashSaleReservationResult.SUCCESS);
        when(requestMapper.insert(any(FlashSaleRequest.class))).thenReturn(1);
        when(outboxMapper.insert(any(FlashSaleOutbox.class))).thenReturn(1);
        FlashSaleProperties properties = new FlashSaleProperties();
        properties.getTopics().setOrder("mr-flash-sale-order-acceptance");

        new FlashSaleService(store, requestMapper, outboxMapper, properties, CLOCK).submit(3, 8L);

        verify(outboxMapper).insert(org.mockito.ArgumentMatchers.<FlashSaleOutbox>argThat(event ->
                "mr-flash-sale-order-acceptance".equals(event.getTopic())));
    }
}
