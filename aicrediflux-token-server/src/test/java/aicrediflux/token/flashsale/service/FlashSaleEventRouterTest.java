package aicrediflux.token.flashsale.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import aicrediflux.token.config.FlashSaleProperties;

class FlashSaleEventRouterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FlashSaleOrderConsumerService orderConsumer = mock(FlashSaleOrderConsumerService.class);
    private final FlashSaleOrderCloseService closeService = mock(FlashSaleOrderCloseService.class);
    private final CreditGrantConsumerService creditConsumer = mock(CreditGrantConsumerService.class);
    private final FlashSaleRequestFailureService failureService = mock(FlashSaleRequestFailureService.class);
    private final FlashSaleProperties properties = acceptanceProperties();
    private final FlashSaleEventRouter router = new FlashSaleEventRouter(
            objectMapper, orderConsumer, closeService, creditConsumer, failureService, properties);

    @Test
    void routesConfiguredOrderTopic() {
        router.route("mr-flash-sale-order-acceptance",
                "{\"requestNo\":\"REQ-1\",\"orderNo\":\"ORDER-1\",\"campaignId\":8,\"userId\":3}");

        verify(orderConsumer).createOrder(any(FlashSaleService.OrderCreateMessage.class));
    }

    @Test
    void routesConfiguredCloseTopic() {
        router.route("mr-flash-sale-delay-close-acceptance", "{\"orderNo\":\"ORDER-1\"}");

        verify(closeService).closeExpired("ORDER-1");
    }

    @Test
    void routesConfiguredCreditTopic() {
        router.route("mr-credit-grant-acceptance",
                "{\"orderNo\":\"ORDER-1\",\"userId\":3,\"creditAmount\":10000}");

        verify(creditConsumer).grant(any(CreditGrantConsumerService.CreditGrantMessage.class));
    }

    private FlashSaleProperties acceptanceProperties() {
        FlashSaleProperties value = new FlashSaleProperties();
        value.getTopics().setOrder("mr-flash-sale-order-acceptance");
        value.getTopics().setClose("mr-flash-sale-delay-close-acceptance");
        value.getTopics().setCredit("mr-credit-grant-acceptance");
        return value;
    }
}
