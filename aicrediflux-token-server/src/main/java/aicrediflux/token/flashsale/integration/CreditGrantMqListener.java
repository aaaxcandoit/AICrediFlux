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
        topic = "${aicrediflux.flash-sale.topics.credit:mr-credit-grant}",
        consumerGroup = "${aicrediflux.flash-sale.consumer-groups.credit:aicrediflux-credit-grant}")
public class CreditGrantMqListener implements RocketMQListener<String> {
    private final FlashSaleEventRouter router;
    private final FlashSaleProperties properties;
    @Override public void onMessage(String payload) { router.route(properties.getTopics().getCredit(), payload); }
}
