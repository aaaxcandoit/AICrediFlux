package aicrediflux.token.flashsale.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CreditGrantLogMapper {
    @Insert("INSERT IGNORE INTO mr_credit_grant_log(user_id,order_no,grant_biz_no,credit_amount,status,create_time,update_time) "
            + "VALUES(#{userId},#{orderNo},#{bizNo},#{amount},'PROCESSING',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))")
    int insertPending(@Param("userId") int userId, @Param("amount") long amount,
                      @Param("bizNo") String bizNo, @Param("orderNo") String orderNo);

    @Update("UPDATE mr_credit_grant_log SET status='SUCCESS',update_time=CURRENT_TIMESTAMP(3) "
            + "WHERE grant_biz_no=#{bizNo} AND status='PROCESSING'")
    int markSuccess(@Param("bizNo") String bizNo);
}
