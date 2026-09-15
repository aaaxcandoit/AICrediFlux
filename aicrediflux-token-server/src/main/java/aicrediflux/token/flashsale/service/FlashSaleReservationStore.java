package aicrediflux.token.flashsale.service;

import java.time.Instant;
import java.util.List;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;

public interface FlashSaleReservationStore {
    FlashSaleReservationResult reserve(long campaignId, int userId, String requestNo, Instant now);
    boolean rollback(long campaignId, int userId, String requestNo);
    void warmup(FlashSaleCampaign campaign);
    void synchronizeStock(long campaignId, int availableStock);
    boolean isReady(long campaignId);
    void reconcile(FlashSaleCampaign campaign, int reservedAvailableStock, List<Integer> qualifiedUsers);
}