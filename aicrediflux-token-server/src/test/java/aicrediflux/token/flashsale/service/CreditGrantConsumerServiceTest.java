package aicrediflux.token.flashsale.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;
import aicrediflux.token.integration.credit.CreditAccountFacade;

class CreditGrantConsumerServiceTest {
    @Test
    void duplicateMessageCreditsWalletOnlyOnce() {
        FlashSaleOrderMapper orderMapper = mock(FlashSaleOrderMapper.class);
        CreditAccountFacade account = mock(CreditAccountFacade.class);
        FlashSaleOrder pending = order("PENDING");
        FlashSaleOrder granted = order("GRANTED");
        when(orderMapper.selectByOrderNoForUpdate("MR-1")).thenReturn(pending, granted);
        when(orderMapper.markCreditGranted("MR-1")).thenReturn(1);
        CreditGrantConsumerService service = new CreditGrantConsumerService(orderMapper, account);

        service.grant(new CreditGrantConsumerService.CreditGrantMessage("MR-1", 12, 500_000L));
        service.grant(new CreditGrantConsumerService.CreditGrantMessage("MR-1", 12, 500_000L));

        verify(account, times(1)).credit(12, 500_000L, "flash-sale:MR-1");
        assertEquals("GRANTED", pending.getCreditStatus());
    }

    private FlashSaleOrder order(String creditStatus) {
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo("MR-1");
        order.setUserId(12);
        order.setCreditAmount(500_000L);
        order.setPayStatus("PAID");
        order.setCreditStatus(creditStatus);
        return order;
    }
}