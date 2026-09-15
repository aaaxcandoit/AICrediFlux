package aicrediflux.token.flashsale.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import aicrediflux.token.config.FlashSaleProperties;

@Service
public class FlashSaleEventRouter {
    private final ObjectMapper objectMapper;
    private final FlashSaleOrderConsumerService orderConsumer;
    private final FlashSaleOrderCloseService closeService;
    private final CreditGrantConsumerService creditConsumer;
    private final FlashSaleRequestFailureService failureService;
    private final FlashSaleProperties properties;

    public FlashSaleEventRouter(ObjectMapper objectMapper, FlashSaleOrderConsumerService orderConsumer,
            FlashSaleOrderCloseService closeService, CreditGrantConsumerService creditConsumer,
            FlashSaleRequestFailureService failureService) {
        this(objectMapper, orderConsumer, closeService, creditConsumer, failureService, new FlashSaleProperties());
    }

    @Autowired
    public FlashSaleEventRouter(ObjectMapper objectMapper, FlashSaleOrderConsumerService orderConsumer,
            FlashSaleOrderCloseService closeService, CreditGrantConsumerService creditConsumer,
            FlashSaleRequestFailureService failureService, FlashSaleProperties properties) {
        this.objectMapper = objectMapper;
        this.orderConsumer = orderConsumer;
        this.closeService = closeService;
        this.creditConsumer = creditConsumer;
        this.failureService = failureService;
        this.properties = properties;
    }

    public void route(String topic, String payload) {
        try {
            if (properties.getTopics().getOrder().equals(topic)) {
                FlashSaleService.OrderCreateMessage message =
                        objectMapper.readValue(payload, FlashSaleService.OrderCreateMessage.class);
                try {
                    orderConsumer.createOrder(message);
                } catch (NonRetryableFlashSaleException e) {
                    failureService.fail(message.requestNo(), e.getMessage());
                }
                return;
            }
            if (properties.getTopics().getClose().equals(topic)) {
                FlashSaleOrderConsumerService.CloseOrderMessage message =
                        objectMapper.readValue(payload, FlashSaleOrderConsumerService.CloseOrderMessage.class);
                closeService.closeExpired(message.orderNo());
                return;
            }
            if (properties.getTopics().getCredit().equals(topic)) {
                CreditGrantConsumerService.CreditGrantMessage message =
                        objectMapper.readValue(payload, CreditGrantConsumerService.CreditGrantMessage.class);
                creditConsumer.grant(message);
                return;
            }
            throw new IllegalArgumentException("不支持的秒杀 Topic: " + topic);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("秒杀事件内容无法解析", e);
        }
    }
}
