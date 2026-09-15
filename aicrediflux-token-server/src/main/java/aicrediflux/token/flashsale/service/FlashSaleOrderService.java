package aicrediflux.token.flashsale.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.yue.library.base.convert.Convert;
import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;
import aicrediflux.token.flashsale.domain.TokenPackage;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleOutboxMapper;
import aicrediflux.token.flashsale.mapper.TokenPackageMapper;

@Service
public class FlashSaleOrderService {
    public static final String CREDIT_TOPIC = "mr-credit-grant";
    private final TokenPackageMapper packageMapper;
    private final FlashSaleOrderMapper orderMapper;
    private final FlashSaleOutboxMapper outboxMapper;
    private final FlashSaleProperties properties;
    private final Clock clock;

    public FlashSaleOrderService(TokenPackageMapper packageMapper, FlashSaleOrderMapper orderMapper,
                                 FlashSaleOutboxMapper outboxMapper) {
        this(packageMapper, orderMapper, outboxMapper, new FlashSaleProperties(), Clock.systemUTC());
    }

    @Autowired
    public FlashSaleOrderService(TokenPackageMapper packageMapper, FlashSaleOrderMapper orderMapper,
                                 FlashSaleOutboxMapper outboxMapper, FlashSaleProperties properties) {
        this(packageMapper, orderMapper, outboxMapper, properties, Clock.systemUTC());
    }

    public FlashSaleOrderService(TokenPackageMapper packageMapper, FlashSaleOrderMapper orderMapper,
                                 FlashSaleOutboxMapper outboxMapper, Clock clock) {
        this(packageMapper, orderMapper, outboxMapper, new FlashSaleProperties(), clock);
    }

    public FlashSaleOrderService(TokenPackageMapper packageMapper, FlashSaleOrderMapper orderMapper,
                                 FlashSaleOutboxMapper outboxMapper, FlashSaleProperties properties, Clock clock) {
        this.packageMapper = packageMapper;
        this.orderMapper = orderMapper;
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlashSaleOrder createOrdinaryOrder(int userId, long packageId) {
        TokenPackage pack = packageMapper.selectById(packageId);
        if (pack == null || !Integer.valueOf(1).equals(pack.getStatus())) {
            throw new IllegalArgumentException("Token 套餐不存在或已下架");
        }
        Instant now = clock.instant();
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo(newOrderNo());
        order.setUserId(userId);
        order.setPackageId(pack.getId());
        order.setPackageName(pack.getName());
        order.setCreditAmount(pack.getCreditAmount());
        order.setPayAmount(pack.getSalePrice());
        order.setOrderSource("ORDINARY");
        order.setOrderStatus("CREATED");
        order.setPayStatus("UNPAID");
        order.setCreditStatus("PENDING");
        order.setExpireTime(now.plus(15, ChronoUnit.MINUTES));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        if (orderMapper.insert(order) != 1) throw new IllegalStateException("订单创建失败");
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlashSaleOrder mockPay(int userId, String orderNo) {
        Instant now = clock.instant();
        FlashSaleOrder order = orderMapper.selectOwnedForUpdate(orderNo, userId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if ("PAID".equals(order.getPayStatus())) return order;
        if (!"CREATED".equals(order.getOrderStatus()) || !"UNPAID".equals(order.getPayStatus())) {
            throw new IllegalStateException("订单当前状态不可支付");
        }
        if (!order.getExpireTime().isAfter(now)) throw new IllegalStateException("订单已过期");
        if (orderMapper.markPaid(orderNo, userId, now) != 1) {
            throw new IllegalStateException("订单状态已变化，请刷新后重试");
        }

        FlashSaleOutbox outbox = new FlashSaleOutbox();
        outbox.setEventId("EVT" + UUID.randomUUID().toString().replace("-", ""));
        outbox.setAggregateNo(orderNo);
        outbox.setEventType("CREDIT_GRANT");
        outbox.setTopic(properties.getTopics().getCredit());
        outbox.setPayload(Convert.toJSONString(
                new CreditGrantConsumerService.CreditGrantMessage(orderNo, userId, order.getCreditAmount())));
        outbox.setPublishStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(now);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        if (outboxMapper.insert(outbox) != 1) throw new IllegalStateException("额度发放事件保存失败");

        order.setOrderStatus("PAID");
        order.setPayStatus("PAID");
        order.setCreditStatus("PENDING");
        order.setPayTime(now);
        return order;
    }

    public List<FlashSaleOrder> listUserOrders(int userId) {
        return orderMapper.selectByUserId(userId);
    }

    public FlashSaleOrder getUserOrder(int userId, String orderNo) {
        FlashSaleOrder order = orderMapper.selectOwned(orderNo, userId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        return order;
    }

    private String newOrderNo() {
        return "MR" + clock.instant().toEpochMilli()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
