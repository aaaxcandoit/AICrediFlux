package aicrediflux.token.relay;

import aicrediflux.token.pojo.vo.PricingVO;
import aicrediflux.token.spi.PricingEnhancer;

import java.util.List;

/**
 * 定价增强默认实现 — 空操作，透传所有定价数据。
 */
public class NoOpPricingEnhancer implements PricingEnhancer {

    @Override
    public void enhance(List<PricingVO> pricingList) {
        // 空操作：透传所有定价数据
    }
}
