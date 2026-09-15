package aicrediflux.token.flashsale.integration;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.service.FlashSaleEventRouter;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aicrediflux.flash-sale.mq-enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${aicrediflux.flash-sale.topics.order:mr-flash-sale-order}",
        consumerGroup = "${aicrediflux.flash-sale.consumer-groups.order:aicrediflux-flash-sale-order}")
public class FlashSaleOrderMqListener implements RocketMQListener<String> {
    private final FlashSaleEventRouter router;
    private final FlashSaleProperties properties;
    @Override public void onMessage(String payload) { router.route(properties.getTopics().getOrder(), payload); }
}
