package aicrediflux.token.flashsale.service;

import aicrediflux.token.flashsale.domain.FlashSaleOutbox;

public interface FlashSaleEventTransport {
    void publish(FlashSaleOutbox event);
}