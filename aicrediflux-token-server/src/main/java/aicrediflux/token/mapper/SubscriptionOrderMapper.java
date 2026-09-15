package aicrediflux.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import aicrediflux.token.pojo.entity.SubscriptionOrder;

/**
 * 订阅订单 Mapper  */
@Mapper
public interface SubscriptionOrderMapper extends BaseMapper<SubscriptionOrder> {
}
