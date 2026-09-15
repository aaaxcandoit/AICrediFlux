package aicrediflux.token.flashsale.service;

import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import aicrediflux.token.config.FlashSaleProperties;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;

class RedisFlashSaleReservationStoreTest {
    @Test
    void warmupCreatesQualificationSetSentinel() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        FlashSaleCampaign campaign = new FlashSaleCampaign();
        campaign.setId(8L);
        campaign.setStatus(1);
        campaign.setAvailableStock(100);
        campaign.setStartTime(Instant.now().minusSeconds(60));
        campaign.setEndTime(Instant.now().plusSeconds(3600));

        new RedisFlashSaleReservationStore(redis).warmup(campaign);

        verify(sets).add("mr:flash:users:8", "__ready__");
        verify(values).setIfAbsent("mr:flash:stock:8", "100");
    }

    @Test
    void warmupUsesConfiguredRedisPrefix() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        FlashSaleProperties properties = new FlashSaleProperties();
        properties.setRedisPrefix("acceptance:mr:flash:");
        FlashSaleCampaign campaign = new FlashSaleCampaign();
        campaign.setId(8L);
        campaign.setStatus(1);
        campaign.setAvailableStock(100);
        campaign.setStartTime(Instant.now().minusSeconds(60));
        campaign.setEndTime(Instant.now().plusSeconds(3600));

        new RedisFlashSaleReservationStore(redis, properties).warmup(campaign);

        verify(sets).add("acceptance:mr:flash:users:8", "__ready__");
        verify(values).setIfAbsent("acceptance:mr:flash:stock:8", "100");
    }
}
