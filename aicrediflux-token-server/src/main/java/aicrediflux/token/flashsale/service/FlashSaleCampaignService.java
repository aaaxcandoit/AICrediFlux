package aicrediflux.token.flashsale.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;
import aicrediflux.token.flashsale.domain.TokenPackage;
import aicrediflux.token.flashsale.mapper.FlashSaleCampaignMapper;
import aicrediflux.token.flashsale.mapper.FlashSaleRequestMapper;
import aicrediflux.token.flashsale.mapper.TokenPackageMapper;

@Service
public class FlashSaleCampaignService {
    private final FlashSaleCampaignMapper campaignMapper;
    private final TokenPackageMapper packageMapper;
    private final FlashSaleRequestMapper requestMapper;
    private final FlashSaleReservationStore reservationStore;
    private final Clock clock;

    @Autowired
    public FlashSaleCampaignService(FlashSaleCampaignMapper campaignMapper, TokenPackageMapper packageMapper,
            FlashSaleRequestMapper requestMapper, FlashSaleReservationStore reservationStore) {
        this(campaignMapper, packageMapper, requestMapper, reservationStore, Clock.systemUTC());
    }

    public FlashSaleCampaignService(FlashSaleCampaignMapper campaignMapper, TokenPackageMapper packageMapper,
            FlashSaleRequestMapper requestMapper, FlashSaleReservationStore reservationStore, Clock clock) {
        this.campaignMapper = campaignMapper;
        this.packageMapper = packageMapper;
        this.requestMapper = requestMapper;
        this.reservationStore = reservationStore;
        this.clock = clock;
    }

    public List<FlashSaleCampaign> listAvailable() { return campaignMapper.selectAvailable(clock.instant()); }
    public List<FlashSaleCampaign> listAll() { return campaignMapper.selectAll(); }

    @Transactional(rollbackFor = Exception.class)
    public FlashSaleCampaign create(FlashSaleCampaign input) {
        validate(input);
        input.setId(null);
        input.setAvailableStock(input.getTotalStock());
        input.setPerUserLimit(1);
        input.setStatus(0);
        input.setVersion(0);
        Instant now = clock.instant();
        input.setCreateTime(now);
        input.setUpdateTime(now);
        if (campaignMapper.insert(input) != 1) throw new IllegalStateException("活动创建失败");
        return input;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlashSaleCampaign update(long id, FlashSaleCampaign input) {
        FlashSaleCampaign existing = require(id);
        if (!Integer.valueOf(0).equals(existing.getStatus())) {
            throw new IllegalStateException("活动发布后不可修改价格、额度和库存");
        }
        validate(input);
        input.setId(id);
        input.setAvailableStock(input.getTotalStock());
        input.setPerUserLimit(1);
        input.setStatus(0);
        input.setVersion(existing.getVersion());
        input.setCreateTime(existing.getCreateTime());
        input.setUpdateTime(clock.instant());
        if (campaignMapper.updateById(input) != 1) throw new IllegalStateException("活动更新失败");
        return input;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlashSaleCampaign publish(long id) {
        FlashSaleCampaign campaign = require(id);
        validate(campaign);
        if (Integer.valueOf(1).equals(campaign.getStatus())) return campaign;
        if (campaignMapper.publish(id) != 1) throw new IllegalStateException("活动状态已变化");
        campaign.setStatus(1);
        reservationStore.warmup(campaign);
        return campaign;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlashSaleCampaign reconcile(long id) {
        FlashSaleCampaign campaign = require(id);
        if (Integer.valueOf(1).equals(campaign.getStatus()) && campaignMapper.pauseForReconcile(id) != 1) {
            throw new IllegalStateException("活动暂停失败");
        }
        if (!Integer.valueOf(1).equals(campaign.getStatus()) && !Integer.valueOf(2).equals(campaign.getStatus())) {
            throw new IllegalStateException("只有已发布或待对账活动可恢复");
        }
        campaign.setStatus(2);
        List<Integer> users = requestMapper.selectQualifiedUsers(id);
        int redisStock = campaign.getAvailableStock() - requestMapper.countPending(id);
        reservationStore.reconcile(campaign, redisStock, users);
        if (campaignMapper.resumeAfterReconcile(id) != 1) throw new IllegalStateException("活动恢复失败");
        campaign.setStatus(1);
        reservationStore.reconcile(campaign, redisStock, users);
        return campaign;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean pauseIfRedisLost(FlashSaleCampaign campaign) {
        if (reservationStore.isReady(campaign.getId())) return false;
        return campaignMapper.pauseForReconcile(campaign.getId()) == 1;
    }

    private FlashSaleCampaign require(long id) {
        FlashSaleCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null) throw new IllegalArgumentException("秒杀活动不存在");
        return campaign;
    }

    private void validate(FlashSaleCampaign campaign) {
        TokenPackage pack = packageMapper.selectById(campaign.getPackageId());
        if (pack == null) throw new IllegalArgumentException("Token 套餐不存在");
        if (campaign.getFlashPrice() == null || campaign.getFlashPrice() < 0
                || campaign.getCreditAmount() == null || campaign.getCreditAmount() <= 0
                || campaign.getTotalStock() == null || campaign.getTotalStock() <= 0) {
            throw new IllegalArgumentException("活动价格、额度或库存无效");
        }
        if (campaign.getStartTime() == null || campaign.getEndTime() == null
                || !campaign.getEndTime().isAfter(campaign.getStartTime())) {
            throw new IllegalArgumentException("活动时间范围无效");
        }
    }
}