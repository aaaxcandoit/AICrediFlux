package aicrediflux.token.flashsale.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import aicrediflux.token.flashsale.domain.FlashSaleRequest;

@Mapper
public interface FlashSaleRequestMapper extends BaseMapper<FlashSaleRequest> {
    @Select("SELECT * FROM mr_flash_sale_request WHERE request_no=#{requestNo} LIMIT 1 FOR UPDATE")
    FlashSaleRequest selectForUpdate(@Param("requestNo") String requestNo);
    @Select("SELECT * FROM mr_flash_sale_request WHERE order_no=#{orderNo} AND user_id=#{userId} LIMIT 1")
    FlashSaleRequest selectOwnedByOrderNo(@Param("orderNo") String orderNo, @Param("userId") int userId);
    @Update("UPDATE mr_flash_sale_request SET process_status='PROCESSED',update_time=CURRENT_TIMESTAMP(3) WHERE request_no=#{requestNo} AND process_status='PENDING'")
    int markProcessed(@Param("requestNo") String requestNo);
    @Update("UPDATE mr_flash_sale_request SET process_status='FAILED',fail_reason=#{reason},update_time=CURRENT_TIMESTAMP(3) WHERE request_no=#{requestNo} AND process_status='PENDING'")
    int markFailed(@Param("requestNo") String requestNo, @Param("reason") String reason);
    @Select("SELECT user_id FROM mr_flash_sale_request WHERE campaign_id=#{campaignId} AND process_status IN ('PENDING','PROCESSED')")
    List<Integer> selectQualifiedUsers(@Param("campaignId") long campaignId);
    @Select("SELECT COUNT(*) FROM mr_flash_sale_request WHERE campaign_id=#{campaignId} AND process_status='PENDING'")
    int countPending(@Param("campaignId") long campaignId);
}