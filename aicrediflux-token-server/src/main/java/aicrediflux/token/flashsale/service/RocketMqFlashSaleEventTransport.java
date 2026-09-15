package aicrediflux.token.flashsale.service;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aicrediflux.flash-sale.mq-enabled", havingValue = "true")
public class RocketMqFlashSaleEventTransport implements FlashSaleEventTransport {
    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void publish(FlashSaleOutbox event) {
        rocketMQTemplate.syncSend(event.getTopic(), event.getPayload(), 5000);
    }
}