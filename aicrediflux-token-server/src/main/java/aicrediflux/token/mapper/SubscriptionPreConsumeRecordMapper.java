package aicrediflux.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import aicrediflux.token.pojo.entity.SubscriptionPreConsumeRecord;

/**
 * 订阅预消费记录 Mapper  */
@Mapper
public interface SubscriptionPreConsumeRecordMapper extends BaseMapper<SubscriptionPreConsumeRecord> {
}
