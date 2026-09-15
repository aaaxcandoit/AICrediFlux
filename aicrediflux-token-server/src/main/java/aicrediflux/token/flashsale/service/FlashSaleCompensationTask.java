package aicrediflux.token.flashsale.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;
import aicrediflux.token.flashsale.mapper.FlashSaleCampaignMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleOrderMapper;

@Slf4j
@Service
@ConditionalOnProperty(name = "aicrediflux.flash-sale.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class FlashSaleCompensationTask {
    private final FlashSaleOrderMapper orderMapper;
    private final FlashSaleCampaignMapper campaignMapper;
    private final FlashSaleOrderCloseService closeService;
    private final CreditGrantConsumerService grantService;
    private final FlashSaleCampaignService campaignService;

    @Scheduled(fixedDelayString = "${aicrediflux.flash-sale.compensation-interval-ms:30000}",
            initialDelayString = "${aicrediflux.flash-sale.compensation-initial-delay-ms:30000}")
    public void compensate() {
        Instant now = Clock.systemUTC().instant();
        for (FlashSaleOrder order : orderMapper.selectExpired(now, 100)) {
            try { closeService.closeExpired(order.getOrderNo()); }
            catch (RuntimeException e) { log.warn("Failed to compensate expired order {}", order.getOrderNo(), e); }
        }
        for (FlashSaleOrder order : orderMapper.selectPaidPendingGrant(100)) {
            try {
                grantService.grant(new CreditGrantConsumerService.CreditGrantMessage(
                        order.getOrderNo(), order.getUserId(), order.getCreditAmount()));
            } catch (RuntimeException e) {
                log.warn("Failed to compensate credit grant {}", order.getOrderNo(), e);
            }
        }
        for (FlashSaleCampaign campaign : campaignMapper.selectAvailable(now)) {
            try {
                if (campaignService.pauseIfRedisLost(campaign)) {
                    log.error("Flash-sale campaign {} paused because Redis state is incomplete; admin reconciliation required",
                            campaign.getId());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to check Redis state for campaign {}", campaign.getId(), e);
            }
        }
    }
}