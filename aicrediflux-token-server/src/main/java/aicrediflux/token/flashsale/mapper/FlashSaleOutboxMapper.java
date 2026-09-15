package aicrediflux.token.flashsale.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import aicrediflux.token.flashsale.domain.FlashSaleOutbox;

@Mapper
public interface FlashSaleOutboxMapper extends BaseMapper<FlashSaleOutbox> {
    @Select("SELECT * FROM mr_flash_sale_outbox WHERE publish_status='PENDING' AND next_retry_time<=#{now} "
            + "ORDER BY id LIMIT #{limit}")
    List<FlashSaleOutbox> selectPending(@Param("now") Instant now, @Param("limit") int limit);

    @Update("UPDATE mr_flash_sale_outbox SET publish_status='PUBLISHED',published_time=#{now},update_time=#{now} "
            + "WHERE id=#{id} AND publish_status='PENDING'")
    int markPublished(@Param("id") long id, @Param("now") Instant now);

    @Update("UPDATE mr_flash_sale_outbox SET retry_count=retry_count+1,next_retry_time=#{next},update_time=#{now} "
            + "WHERE id=#{id} AND publish_status='PENDING'")
    int markRetry(@Param("id") long id, @Param("next") Instant next, @Param("now") Instant now);
}
