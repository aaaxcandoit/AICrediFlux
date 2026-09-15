package aicrediflux.token.flashsale.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aicrediflux.flash-sale.mq-enabled", havingValue = "false", matchIfMissing = true)
public class LocalFlashSaleEventTransport implements FlashSaleEventTransport {
    private final FlashSaleEventRouter router;

    @Override
    public void publish(FlashSaleOutbox event) {
        router.route(event.getTopic(), event.getPayload());
    }
}