package aicrediflux.token.flashsale.service;

import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;
import aicrediflux.token.flashsale.mapper.FlashSaleOutboxMapper;

class FlashSaleOutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-09-07T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void confirmsOnlySuccessfullyDeliveredEventsAndRetriesFailures() {
        FlashSaleOutboxMapper mapper = mock(FlashSaleOutboxMapper.class);
        FlashSaleEventTransport transport = mock(FlashSaleEventTransport.class);
        FlashSaleOutbox ok = event(1L);
        FlashSaleOutbox failed = event(2L);
        when(mapper.selectPending(NOW, 100)).thenReturn(List.of(ok, failed));
        doThrow(new IllegalStateException("broker unavailable")).when(transport).publish(failed);
        FlashSaleOutboxPublisher publisher = new FlashSaleOutboxPublisher(mapper, transport, CLOCK);

        publisher.publishPending();

        verify(mapper).markPublished(1L, NOW);
        verify(mapper, never()).markPublished(eq(2L), any());
        verify(mapper).markRetry(eq(2L), eq(NOW.plusSeconds(5)), eq(NOW));
    }

    @Test
    void timedOutDeliveryRemainsPendingAndPublishesOnRetry() {
        FlashSaleOutboxMapper mapper = mock(FlashSaleOutboxMapper.class);
        FlashSaleEventTransport transport = mock(FlashSaleEventTransport.class);
        FlashSaleOutbox event = event(7L);
        when(mapper.selectPending(NOW, 100)).thenReturn(List.of(event), List.of(event));
        doThrow(new IllegalStateException("send timeout")).doNothing().when(transport).publish(event);
        FlashSaleOutboxPublisher publisher = new FlashSaleOutboxPublisher(mapper, transport, CLOCK);

        publisher.publishPending();
        publisher.publishPending();

        verify(transport, times(2)).publish(event);
        verify(mapper).markRetry(7L, NOW.plusSeconds(5), NOW);
        verify(mapper).markPublished(7L, NOW);
    }

    private FlashSaleOutbox event(long id) {
        FlashSaleOutbox event = new FlashSaleOutbox();
        event.setId(id);
        event.setRetryCount(0);
        return event;
    }
}
