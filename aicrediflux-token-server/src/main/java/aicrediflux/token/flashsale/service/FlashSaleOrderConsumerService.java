package aicrediflux.token.flashsale.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.yue.library.base.convert.Convert;
import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.domain.*;
import aicrediflux.token.flashsale.mapper.*;

@Service
public class FlashSaleOrderConsumerService {
    public static final String CLOSE_TOPIC = "mr-flash-sale-delay-close";
    private final FlashSaleRequestMapper requestMapper;
    private final FlashSaleCampaignMapper campaignMapper;
    private final TokenPackageMapper packageMapper;
    private final FlashSaleOrderMapper orderMapper;
    private final FlashSaleOutboxMapper outboxMapper;
    private final FlashSaleProperties properties;
    private final Clock clock;

    public FlashSaleOrderConsumerService(FlashSaleRequestMapper requestMapper, FlashSaleCampaignMapper campaignMapper,
            TokenPackageMapper packageMapper, FlashSaleOrderMapper orderMapper, FlashSaleOutboxMapper outboxMapper) {
        this(requestMapper, campaignMapper, packageMapper, orderMapper, outboxMapper,
                new FlashSaleProperties(), Clock.systemUTC());
    }

    @Autowired
    public FlashSaleOrderConsumerService(FlashSaleRequestMapper requestMapper, FlashSaleCampaignMapper campaignMapper,
            TokenPackageMapper packageMapper, FlashSaleOrderMapper orderMapper, FlashSaleOutboxMapper outboxMapper,
            FlashSaleProperties properties) {
        this(requestMapper, campaignMapper, packageMapper, orderMapper, outboxMapper, properties, Clock.systemUTC());
    }

    public FlashSaleOrderConsumerService(FlashSaleRequestMapper requestMapper, FlashSaleCampaignMapper campaignMapper,
            TokenPackageMapper packageMapper, FlashSaleOrderMapper orderMapper, FlashSaleOutboxMapper outboxMapper, Clock clock) {
        this(requestMapper, campaignMapper, packageMapper, orderMapper, outboxMapper,
                new FlashSaleProperties(), clock);
    }

    public FlashSaleOrderConsumerService(FlashSaleRequestMapper requestMapper, FlashSaleCampaignMapper campaignMapper,
            TokenPackageMapper packageMapper, FlashSaleOrderMapper orderMapper, FlashSaleOutboxMapper outboxMapper,
            FlashSaleProperties properties, Clock clock) {
        this.requestMapper = requestMapper;
        this.campaignMapper = campaignMapper;
        this.packageMapper = packageMapper;
        this.orderMapper = orderMapper;
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlashSaleOrder createOrder(FlashSaleService.OrderCreateMessage message) {
        FlashSaleRequest request = requestMapper.selectForUpdate(message.requestNo());
        if (request == null) throw new NonRetryableFlashSaleException("秒杀请求不存在");
        if ("PROCESSED".equals(request.getProcessStatus())) return orderMapper.selectByOrderNo(message.orderNo());
        if (!"PENDING".equals(request.getProcessStatus())) throw new NonRetryableFlashSaleException("秒杀请求已终止");
        FlashSaleCampaign campaign = campaignMapper.selectById(message.campaignId());
        if (campaign == null || !Integer.valueOf(1).equals(campaign.getStatus())) {
            throw new NonRetryableFlashSaleException("秒杀活动不存在或未启用");
        }
        TokenPackage pack = packageMapper.selectById(campaign.getPackageId());
        if (pack == null) throw new NonRetryableFlashSaleException("Token 套餐不存在");
        if (campaignMapper.decreaseStock(campaign.getId()) != 1) {
            throw new NonRetryableFlashSaleException("数据库库存不足");
        }
        Instant now = clock.instant();
        Instant expireAt = now.plus(15, ChronoUnit.MINUTES);
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo(message.orderNo());
        order.setUserId(message.userId());
        order.setPackageId(pack.getId());
        order.setCampaignId(campaign.getId());
        order.setPackageName(pack.getName());
        order.setCreditAmount(campaign.getCreditAmount());
        order.setPayAmount(campaign.getFlashPrice());
        order.setOrderSource("FLASH_SALE");
        order.setOrderStatus("CREATED");
        order.setPayStatus("UNPAID");
        order.setCreditStatus("PENDING");
        order.setExpireTime(expireAt);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        if (orderMapper.insert(order) != 1) throw new IllegalStateException("秒杀订单创建失败");
        if (requestMapper.markProcessed(message.requestNo()) != 1) throw new IllegalStateException("秒杀请求状态更新失败");

        FlashSaleOutbox close = new FlashSaleOutbox();
        close.setEventId("EVT" + UUID.randomUUID().toString().replace("-", ""));
        close.setAggregateNo(order.getOrderNo());
        close.setEventType("ORDER_CLOSE");
        close.setTopic(properties.getTopics().getClose());
        close.setPayload(Convert.toJSONString(new CloseOrderMessage(order.getOrderNo())));
        close.setDeliverAt(expireAt);
        close.setPublishStatus("PENDING");
        close.setRetryCount(0);
        close.setNextRetryTime(expireAt);
        close.setCreateTime(now);
        close.setUpdateTime(now);
        if (outboxMapper.insert(close) != 1) throw new IllegalStateException("关单事件保存失败");
        return order;
    }

    public record CloseOrderMessage(String orderNo) {}
}
