package aicrediflux.token.integration.credit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CreditAccountMapper {
    @Select("SELECT quota FROM users WHERE id=#{userId}")
    Long selectQuota(@Param("userId") int userId);

    @Update("UPDATE users SET quota=quota+#{amount} WHERE id=#{userId}")
    int increaseQuota(@Param("userId") int userId, @Param("amount") long amount);
}
