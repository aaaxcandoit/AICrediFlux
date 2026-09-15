package aicrediflux.token.flashsale.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import aicrediflux.token.flashsale.domain.FlashSaleCampaign;

@Mapper
public interface FlashSaleCampaignMapper extends BaseMapper<FlashSaleCampaign> {
    @Update("UPDATE mr_flash_sale_campaign SET available_stock=available_stock-1,version=version+1 WHERE id=#{id} AND status=1 AND available_stock>0")
    int decreaseStock(@Param("id") long id);
    @Update("UPDATE mr_flash_sale_campaign SET available_stock=LEAST(total_stock,available_stock+1),version=version+1 WHERE id=#{id}")
    int restoreStock(@Param("id") long id);
    @Select("SELECT * FROM mr_flash_sale_campaign WHERE status=1 AND end_time>#{now} ORDER BY start_time,id")
    List<FlashSaleCampaign> selectAvailable(@Param("now") Instant now);
    @Select("SELECT * FROM mr_flash_sale_campaign ORDER BY id DESC")
    List<FlashSaleCampaign> selectAll();
    @Update("UPDATE mr_flash_sale_campaign SET status=1,update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status=0")
    int publish(@Param("id") long id);
    @Update("UPDATE mr_flash_sale_campaign SET status=2,update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status=1")
    int pauseForReconcile(@Param("id") long id);
    @Update("UPDATE mr_flash_sale_campaign SET status=1,update_time=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status=2")
    int resumeAfterReconcile(@Param("id") long id);
}