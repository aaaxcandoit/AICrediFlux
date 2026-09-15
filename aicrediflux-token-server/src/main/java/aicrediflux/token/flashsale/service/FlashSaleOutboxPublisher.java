package aicrediflux.token.flashsale.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;
import aicrediflux.token.flashsale.mapper.FlashSaleOutboxMapper;

@Slf4j
@Service
@ConditionalOnProperty(name = "aicrediflux.flash-sale.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleOutboxPublisher {
    private final FlashSaleOutboxMapper outboxMapper;
    private final FlashSaleEventTransport transport;
    private final Clock clock;

    @Autowired
    public FlashSaleOutboxPublisher(FlashSaleOutboxMapper outboxMapper, FlashSaleEventTransport transport) {
        this(outboxMapper, transport, Clock.systemUTC());
    }

    public FlashSaleOutboxPublisher(FlashSaleOutboxMapper outboxMapper, FlashSaleEventTransport transport, Clock clock) {
        this.outboxMapper = outboxMapper;
        this.transport = transport;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${aicrediflux.flash-sale.outbox-interval-ms:1000}")
    public void publishPending() {
        Instant now = clock.instant();
        for (FlashSaleOutbox event : outboxMapper.selectPending(now, 100)) {
            try {
                transport.publish(event);
                outboxMapper.markPublished(event.getId(), now);
            } catch (RuntimeException e) {
                long delay = Math.min(300L, 5L << Math.min(event.getRetryCount(), 6));
                outboxMapper.markRetry(event.getId(), now.plusSeconds(delay), now);
                log.warn("Flash-sale outbox delivery failed, eventId={}, retryIn={}s",
                        event.getEventId(), delay, e);
            }
        }
    }
}