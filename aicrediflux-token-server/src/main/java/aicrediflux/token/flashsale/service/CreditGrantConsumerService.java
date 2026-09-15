package aicrediflux.token.flashsale.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;
import aicrediflux.token.integration.credit.CreditAccountFacade;

@Service
@RequiredArgsConstructor
public class CreditGrantConsumerService {
    private final FlashSaleOrderMapper orderMapper;
    private final CreditAccountFacade creditAccount;

    @Transactional(rollbackFor = Exception.class)
    public void grant(CreditGrantMessage message) {
        FlashSaleOrder order = orderMapper.selectByOrderNoForUpdate(message.orderNo());
        if (order == null) throw new NonRetryableFlashSaleException("订单不存在");
        if ("GRANTED".equals(order.getCreditStatus())) return;
        if (!"PAID".equals(order.getPayStatus())) {
            throw new NonRetryableFlashSaleException("未支付订单不可发放额度");
        }
        if (order.getUserId() != message.userId()
                || !order.getCreditAmount().equals(message.creditAmount())) {
            throw new NonRetryableFlashSaleException("额度发放消息与订单快照不一致");
        }
        creditAccount.credit(message.userId(), message.creditAmount(), "flash-sale:" + message.orderNo());
        if (orderMapper.markCreditGranted(message.orderNo()) != 1) {
            throw new IllegalStateException("订单额度状态更新失败");
        }
        order.setCreditStatus("GRANTED");
    }

    public record CreditGrantMessage(String orderNo, int userId, long creditAmount) {}
}