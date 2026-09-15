package aicrediflux.token.flashsale.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.flashsale.domain.FlashSaleRequest;
import aicrediflux.token.flashsale.mapper.FlashSaleRequestMapper;

@Service
@RequiredArgsConstructor
public class FlashSaleRequestFailureService {
    private final FlashSaleRequestMapper requestMapper;
    private final FlashSaleReservationStore reservationStore;

    @Transactional(rollbackFor = Exception.class)
    public void fail(String requestNo, String reason) {
        FlashSaleRequest request = requestMapper.selectForUpdate(requestNo);
        if (request == null || !"PENDING".equals(request.getProcessStatus())) return;
        if (requestMapper.markFailed(requestNo, truncate(reason)) != 1) {
            throw new IllegalStateException("秒杀请求失败状态保存失败");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reservationStore.rollback(request.getCampaignId(), request.getUserId(), requestNo);
            }
        });
    }

    private String truncate(String reason) {
        if (reason == null) return "永久处理失败";
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}