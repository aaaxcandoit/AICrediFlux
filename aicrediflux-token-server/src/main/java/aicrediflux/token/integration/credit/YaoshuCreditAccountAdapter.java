package aicrediflux.token.integration.credit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import aicrediflux.token.flashsale.mapper.CreditGrantLogMapper;

@Service
@RequiredArgsConstructor
public class YaoshuCreditAccountAdapter implements CreditAccountFacade {
    private final CreditAccountMapper accountMapper;
    private final CreditGrantLogMapper grantLogMapper;

    @Override
    public long getBalance(int userId) {
        Long quota = accountMapper.selectQuota(userId);
        if (quota == null) throw new IllegalArgumentException("用户不存在");
        return quota;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void credit(int userId, long amount, String bizNo) {
        if (userId <= 0 || amount <= 0 || bizNo == null || bizNo.isBlank()) {
            throw new IllegalArgumentException("无效的额度发放参数");
        }
        String orderNo = bizNo.startsWith("flash-sale:") ? bizNo.substring("flash-sale:".length()) : bizNo;
        if (grantLogMapper.insertPending(userId, amount, bizNo, orderNo) == 0) return;
        if (accountMapper.increaseQuota(userId, amount) != 1) throw new IllegalStateException("用户额度发放失败");
        if (grantLogMapper.markSuccess(bizNo) != 1) throw new IllegalStateException("额度发放日志状态更新失败");
    }
}
