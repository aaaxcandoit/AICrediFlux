package aicrediflux.token.flashsale.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.yue.library.base.convert.Convert;
import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;
import aicrediflux.token.flashsale.domain.FlashSaleRequest;
import aicrediflux.token.flashsale.mapper.FlashSaleOutboxMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleRequestMapper;

@Service
public class FlashSaleService {
    public static final String ORDER_TOPIC = "mr-flash-sale-order";
    private final FlashSaleReservationStore reservationStore;
    private final FlashSaleRequestMapper requestMapper;
    private final FlashSaleOutboxMapper outboxMapper;
    private final FlashSaleProperties properties;
    private final Clock clock;

    public FlashSaleService(FlashSaleReservationStore reservationStore, FlashSaleRequestMapper requestMapper,
                            FlashSaleOutboxMapper outboxMapper) {
        this(reservationStore, requestMapper, outboxMapper, new FlashSaleProperties(), Clock.systemUTC());
    }

    @Autowired
    public FlashSaleService(FlashSaleReservationStore reservationStore, FlashSaleRequestMapper requestMapper,
                            FlashSaleOutboxMapper outboxMapper, FlashSaleProperties properties) {
        this(reservationStore, requestMapper, outboxMapper, properties, Clock.systemUTC());
    }

    public FlashSaleService(FlashSaleReservationStore reservationStore, FlashSaleRequestMapper requestMapper,
                            FlashSaleOutboxMapper outboxMapper, Clock clock) {
        this(reservationStore, requestMapper, outboxMapper, new FlashSaleProperties(), clock);
    }

    public FlashSaleService(FlashSaleReservationStore reservationStore, FlashSaleRequestMapper requestMapper,
                            FlashSaleOutboxMapper outboxMapper, FlashSaleProperties properties, Clock clock) {
        this.reservationStore = reservationStore;
        this.requestMapper = requestMapper;
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlashSaleSubmission submit(int userId, long campaignId) {
        String requestNo = id("REQ");
        String orderNo = id("MR");
        FlashSaleReservationResult result = reservationStore.reserve(campaignId, userId, requestNo, clock.instant());
        if (result != FlashSaleReservationResult.SUCCESS) throw new FlashSaleRejectedException(result.message());
        try {
            Instant now = clock.instant();
            FlashSaleRequest request = new FlashSaleRequest();
            request.setRequestNo(requestNo);
            request.setOrderNo(orderNo);
            request.setCampaignId(campaignId);
            request.setUserId(userId);
            request.setProcessStatus("PENDING");
            request.setCreateTime(now);
            request.setUpdateTime(now);
            if (requestMapper.insert(request) != 1) throw new IllegalStateException("秒杀请求保存失败");

            FlashSaleOutbox outbox = new FlashSaleOutbox();
            outbox.setEventId(id("EVT"));
            outbox.setAggregateNo(orderNo);
            outbox.setEventType("ORDER_CREATE");
            outbox.setTopic(properties.getTopics().getOrder());
            outbox.setPayload(Convert.toJSONString(new OrderCreateMessage(requestNo, orderNo, campaignId, userId)));
            outbox.setPublishStatus("PENDING");
            outbox.setRetryCount(0);
            outbox.setNextRetryTime(now);
            outbox.setCreateTime(now);
            outbox.setUpdateTime(now);
            if (outboxMapper.insert(outbox) != 1) throw new IllegalStateException("秒杀事件保存失败");
            return new FlashSaleSubmission(requestNo, orderNo, "PENDING");
        } catch (RuntimeException e) {
            reservationStore.rollback(campaignId, userId, requestNo);
            throw e;
        }
    }

    private String id(String prefix) {
        return prefix + clock.instant().toEpochMilli() + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public record OrderCreateMessage(String requestNo, String orderNo, long campaignId, int userId) {}
}
