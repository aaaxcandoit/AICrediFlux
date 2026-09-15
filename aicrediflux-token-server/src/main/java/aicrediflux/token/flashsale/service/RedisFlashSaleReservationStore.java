package aicrediflux.token.flashsale.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;

@Component
public class RedisFlashSaleReservationStore implements FlashSaleReservationStore {
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = script("flashsale/reserve.lua");
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = script("flashsale/rollback.lua");
    private static final DefaultRedisScript<Long> RECONCILE_SCRIPT = script("flashsale/reconcile.lua");
    private final StringRedisTemplate redis;
    private final String prefix;

    public RedisFlashSaleReservationStore(StringRedisTemplate redis) {
        this(redis, new FlashSaleProperties());
    }

    @Autowired
    public RedisFlashSaleReservationStore(StringRedisTemplate redis, FlashSaleProperties properties) {
        this.redis = redis;
        this.prefix = properties.getRedisPrefix();
    }

    @Override
    public FlashSaleReservationResult reserve(long campaignId, int userId, String requestNo, Instant now) {
        Long code = redis.execute(RESERVE_SCRIPT,
                List.of(campaignKey(campaignId), stockKey(campaignId), usersKey(campaignId), reservationKey(campaignId, userId)),
                String.valueOf(userId), requestNo, String.valueOf(now.toEpochMilli()));
        if (code == null) throw new IllegalStateException("秒杀脚本没有返回结果");
        return FlashSaleReservationResult.fromCode(code);
    }

    @Override
    public boolean rollback(long campaignId, int userId, String requestNo) {
        Long changed = redis.execute(ROLLBACK_SCRIPT,
                List.of(stockKey(campaignId), usersKey(campaignId), reservationKey(campaignId, userId)),
                String.valueOf(userId), requestNo);
        return changed != null && changed == 1L;
    }

    @Override
    public void warmup(FlashSaleCampaign campaign) {
        String key = campaignKey(campaign.getId());
        redis.opsForHash().putAll(key, Map.of("status", String.valueOf(campaign.getStatus()),
                "startMs", String.valueOf(campaign.getStartTime().toEpochMilli()),
                "endMs", String.valueOf(campaign.getEndTime().toEpochMilli())));
        redis.opsForValue().setIfAbsent(stockKey(campaign.getId()), String.valueOf(campaign.getAvailableStock()));
        redis.opsForSet().add(usersKey(campaign.getId()), "__ready__");
        expireTogether(campaign);
    }

    @Override
    public void synchronizeStock(long campaignId, int availableStock) {
        redis.opsForValue().set(stockKey(campaignId), String.valueOf(Math.max(availableStock, 0)));
    }

    @Override
    public boolean isReady(long campaignId) {
        return Boolean.TRUE.equals(redis.hasKey(campaignKey(campaignId)))
                && Boolean.TRUE.equals(redis.hasKey(stockKey(campaignId)))
                && Boolean.TRUE.equals(redis.hasKey(usersKey(campaignId)));
    }

    @Override
    public void reconcile(FlashSaleCampaign campaign, int reservedAvailableStock, List<Integer> qualifiedUsers) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(campaign.getStatus()));
        args.add(String.valueOf(campaign.getStartTime().toEpochMilli()));
        args.add(String.valueOf(campaign.getEndTime().toEpochMilli()));
        args.add(String.valueOf(Math.max(reservedAvailableStock, 0)));
        args.add(String.valueOf(campaign.getEndTime().plus(Duration.ofDays(2)).toEpochMilli()));
        qualifiedUsers.forEach(userId -> args.add(String.valueOf(userId)));
        redis.execute(RECONCILE_SCRIPT,
                List.of(campaignKey(campaign.getId()), stockKey(campaign.getId()), usersKey(campaign.getId())),
                args.toArray());
    }

    private void expireTogether(FlashSaleCampaign campaign) {
        Duration ttl = Duration.between(Instant.now(), campaign.getEndTime().plus(Duration.ofDays(2)));
        if (!ttl.isNegative() && !ttl.isZero()) {
            redis.expire(campaignKey(campaign.getId()), ttl);
            redis.expire(stockKey(campaign.getId()), ttl);
            redis.expire(usersKey(campaign.getId()), ttl);
        }
    }

    private String campaignKey(long id) { return prefix + "campaign:" + id; }
    private String stockKey(long id) { return prefix + "stock:" + id; }
    private String usersKey(long id) { return prefix + "users:" + id; }
    private String reservationKey(long id, int userId) { return prefix + "reservation:" + id + ":" + userId; }
    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
