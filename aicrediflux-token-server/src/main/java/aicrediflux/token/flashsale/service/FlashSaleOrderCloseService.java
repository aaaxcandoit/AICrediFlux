package aicrediflux.token.flashsale.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.mapper.FlashSaleCampaignMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;

@Service
public class FlashSaleOrderCloseService {
    private final FlashSaleOrderMapper orderMapper;
    private final FlashSaleCampaignMapper campaignMapper;
    private final FlashSaleReservationStore reservationStore;
    private final Clock clock;

    @Autowired
    public FlashSaleOrderCloseService(FlashSaleOrderMapper orderMapper, FlashSaleCampaignMapper campaignMapper,
                                      FlashSaleReservationStore reservationStore) {
        this(orderMapper, campaignMapper, reservationStore, Clock.systemUTC());
    }

    public FlashSaleOrderCloseService(FlashSaleOrderMapper orderMapper, FlashSaleCampaignMapper campaignMapper,
                                      FlashSaleReservationStore reservationStore, Clock clock) {
        this.orderMapper = orderMapper;
        this.campaignMapper = campaignMapper;
        this.reservationStore = reservationStore;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean closeExpired(String orderNo) {
        Instant now = clock.instant();
        FlashSaleOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null || !"CREATED".equals(order.getOrderStatus()) || !"UNPAID".equals(order.getPayStatus())) return false;
        if (order.getExpireTime().isAfter(now)) return false;
        if (orderMapper.markClosed(orderNo, now) != 1) return false;
        if (order.getCampaignId() != null) {
            if (campaignMapper.restoreStock(order.getCampaignId()) != 1) throw new IllegalStateException("活动库存恢复失败");
            FlashSaleCampaign campaign = campaignMapper.selectById(order.getCampaignId());
            if (campaign != null) reservationStore.synchronizeStock(campaign.getId(), campaign.getAvailableStock());
        }
        return true;
    }
}
