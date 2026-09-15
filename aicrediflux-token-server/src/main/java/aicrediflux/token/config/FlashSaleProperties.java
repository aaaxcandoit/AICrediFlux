package aicrediflux.token.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aicrediflux.flash-sale")
public class FlashSaleProperties {
    private String redisPrefix = "mr:flash:";
    private Topics topics = new Topics();
    private ConsumerGroups consumerGroups = new ConsumerGroups();

    @Data
    public static class Topics {
        private String order = "mr-flash-sale-order";
        private String close = "mr-flash-sale-delay-close";
        private String credit = "mr-credit-grant";
    }

    @Data
    public static class ConsumerGroups {
        private String order = "aicrediflux-flash-sale-order";
        private String close = "aicrediflux-flash-sale-close";
        private String credit = "aicrediflux-credit-grant";
    }
}
