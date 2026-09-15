package aicrediflux.token.flashsale.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import aicrediflux.token.flashsale.domain.FlashSaleOrder;

@Mapper
public interface FlashSaleOrderMapper extends BaseMapper<FlashSaleOrder> {
    @Select("SELECT * FROM mr_flash_sale_order WHERE order_no=#{orderNo} LIMIT 1")
    FlashSaleOrder selectByOrderNo(@Param("orderNo") String orderNo);
    @Select("SELECT * FROM mr_flash_sale_order WHERE order_no=#{orderNo} LIMIT 1 FOR UPDATE")
    FlashSaleOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);
    @Update("UPDATE mr_flash_sale_order SET order_status='CLOSED',close_time=#{now},stock_restored=1,update_time=#{now} WHERE order_no=#{orderNo} AND order_status='CREATED' AND pay_status='UNPAID' AND expire_time<=#{now} AND stock_restored=0")
    int markClosed(@Param("orderNo") String orderNo, @Param("now") Instant now);
    @Select("SELECT * FROM mr_flash_sale_order WHERE order_no=#{orderNo} AND user_id=#{userId} LIMIT 1 FOR UPDATE")
    FlashSaleOrder selectOwnedForUpdate(@Param("orderNo") String orderNo, @Param("userId") int userId);
    @Select("SELECT * FROM mr_flash_sale_order WHERE order_no=#{orderNo} AND user_id=#{userId} LIMIT 1")
    FlashSaleOrder selectOwned(@Param("orderNo") String orderNo, @Param("userId") int userId);
    @Select("SELECT * FROM mr_flash_sale_order WHERE user_id=#{userId} ORDER BY id DESC")
    List<FlashSaleOrder> selectByUserId(@Param("userId") int userId);
    @Select("SELECT * FROM mr_flash_sale_order ORDER BY id DESC LIMIT #{limit}")
    List<FlashSaleOrder> selectRecent(@Param("limit") int limit);
    @Select("SELECT * FROM mr_flash_sale_order WHERE order_status='CREATED' AND pay_status='UNPAID' AND expire_time<=#{now} ORDER BY id LIMIT #{limit}")
    List<FlashSaleOrder> selectExpired(@Param("now") Instant now, @Param("limit") int limit);
    @Select("SELECT * FROM mr_flash_sale_order WHERE pay_status='PAID' AND credit_status='PENDING' ORDER BY id LIMIT #{limit}")
    List<FlashSaleOrder> selectPaidPendingGrant(@Param("limit") int limit);
    @Update("UPDATE mr_flash_sale_order SET order_status='PAID',pay_status='PAID',pay_time=#{now},update_time=#{now} WHERE order_no=#{orderNo} AND user_id=#{userId} AND order_status='CREATED' AND pay_status='UNPAID' AND expire_time>#{now}")
    int markPaid(@Param("orderNo") String orderNo, @Param("userId") int userId, @Param("now") Instant now);
    @Update("UPDATE mr_flash_sale_order SET credit_status='GRANTED',update_time=CURRENT_TIMESTAMP(3) WHERE order_no=#{orderNo} AND credit_status='PENDING'")
    int markCreditGranted(@Param("orderNo") String orderNo);
}