package aicrediflux.token.flashsale.integration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.service.FlashSaleEventRouter;

class FlashSaleMqListenerTest {
    private final FlashSaleEventRouter router = mock(FlashSaleEventRouter.class);
    private final FlashSaleProperties properties = acceptanceProperties();

    @Test
    void orderListenerRoutesWithConfiguredTopic() {
        new FlashSaleOrderMqListener(router, properties).onMessage("{}");

        verify(router).route("mr-flash-sale-order-acceptance", "{}");
    }

    @Test
    void closeListenerRoutesWithConfiguredTopic() {
        new FlashSaleCloseMqListener(router, properties).onMessage("{}");

        verify(router).route("mr-flash-sale-delay-close-acceptance", "{}");
    }

    @Test
    void creditListenerRoutesWithConfiguredTopic() {
        new CreditGrantMqListener(router, properties).onMessage("{}");

        verify(router).route("mr-credit-grant-acceptance", "{}");
    }

    private FlashSaleProperties acceptanceProperties() {
        FlashSaleProperties value = new FlashSaleProperties();
        value.getTopics().setOrder("mr-flash-sale-order-acceptance");
        value.getTopics().setClose("mr-flash-sale-delay-close-acceptance");
        value.getTopics().setCredit("mr-credit-grant-acceptance");
        return value;
    }
}
