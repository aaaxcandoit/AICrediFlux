package aicrediflux.token.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import aicrediflux.token.relay.DefaultChannelHealthHandler;
import aicrediflux.token.relay.DefaultChannelSelector;
import aicrediflux.token.relay.DefaultRelayRetryStrategy;
import aicrediflux.token.relay.NoOpChannelModelSyncHandler;
import aicrediflux.token.relay.NoOpModelListFilter;
import aicrediflux.token.relay.NoOpPricingEnhancer;
import aicrediflux.token.relay.NoOpRelayRequestInterceptor;
import aicrediflux.token.service.ChannelManagementService;
import aicrediflux.token.service.ChannelService;
import aicrediflux.token.spi.ChannelHealthHandler;
import aicrediflux.token.spi.ChannelModelSyncHandler;
import aicrediflux.token.spi.ChannelSelector;
import aicrediflux.token.spi.ModelListFilter;
import aicrediflux.token.spi.PricingEnhancer;
import aicrediflux.token.spi.RelayRequestInterceptor;
import aicrediflux.token.spi.RelayRetryStrategy;

/**
 * SPI 自动装配 — 默认实现注册。
 * <p>
 * 所有默认 Bean 均标注 {@code @ConditionalOnMissingBean}：
 * 存在自定义实现时自动跳过默认 Bean；
 * 无自定义实现时无缝退化到默认实现。
 */
@Configuration
public class SpiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RelayRequestInterceptor.class)
    public RelayRequestInterceptor defaultRelayRequestInterceptor() {
        return new NoOpRelayRequestInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(RelayRetryStrategy.class)
    public RelayRetryStrategy defaultRelayRetryStrategy() {
        return new DefaultRelayRetryStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(ChannelHealthHandler.class)
    public ChannelHealthHandler defaultChannelHealthHandler(ChannelService channelService,
                                                            ChannelManagementService channelManagementService) {
        return new DefaultChannelHealthHandler(channelService, channelManagementService);
    }

    @Bean
    @ConditionalOnMissingBean(ChannelSelector.class)
    public ChannelSelector defaultChannelSelector() {
        return new DefaultChannelSelector();
    }

    @Bean
    @ConditionalOnMissingBean(ModelListFilter.class)
    public ModelListFilter defaultModelListFilter() {
        return new NoOpModelListFilter();
    }

    @Bean
    @ConditionalOnMissingBean(PricingEnhancer.class)
    public PricingEnhancer defaultPricingEnhancer() {
        return new NoOpPricingEnhancer();
    }

    @Bean
    @ConditionalOnMissingBean(ChannelModelSyncHandler.class)
    public ChannelModelSyncHandler defaultChannelModelSyncHandler() {
        return new NoOpChannelModelSyncHandler();
    }
}
