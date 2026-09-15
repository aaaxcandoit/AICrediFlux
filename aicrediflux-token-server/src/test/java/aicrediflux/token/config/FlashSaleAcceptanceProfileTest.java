package aicrediflux.token.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class FlashSaleAcceptanceProfileTest {
    @Test
    void acceptanceProfileBindsIsolatedDatabaseRedisAndMqNames() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("acceptance",
                new ClassPathResource("application-acceptance.yml"))
                .forEach(environment.getPropertySources()::addFirst);

        FlashSaleProperties properties = Binder.get(environment)
                .bind("aicrediflux.flash-sale", Bindable.of(FlashSaleProperties.class))
                .orElseThrow(() -> new AssertionError("acceptance flash-sale properties were not bound"));

        assertTrue(environment.getProperty(
                "spring.datasource.dynamic.datasource.aicrediflux.url", "").contains("aicrediflux_acceptance"));
        assertEquals(14, environment.getProperty("spring.data.redis.database", Integer.class));
        assertEquals("acceptance:mr:flash:", properties.getRedisPrefix());
        assertEquals("mr-flash-sale-order-acceptance", properties.getTopics().getOrder());
        assertEquals("mr-flash-sale-delay-close-acceptance", properties.getTopics().getClose());
        assertEquals("mr-credit-grant-acceptance", properties.getTopics().getCredit());
        assertEquals("aicrediflux-flash-sale-order-acceptance", properties.getConsumerGroups().getOrder());
        assertEquals("aicrediflux-flash-sale-close-acceptance", properties.getConsumerGroups().getClose());
        assertEquals("aicrediflux-credit-grant-acceptance", properties.getConsumerGroups().getCredit());
    }
}
