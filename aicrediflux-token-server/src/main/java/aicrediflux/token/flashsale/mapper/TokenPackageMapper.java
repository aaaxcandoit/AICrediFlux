package aicrediflux.token.flashsale.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import aicrediflux.token.flashsale.domain.TokenPackage;

@Mapper
public interface TokenPackageMapper extends BaseMapper<TokenPackage> {
    @Select("SELECT * FROM mr_token_package WHERE status=1 ORDER BY sort,id")
    List<TokenPackage> selectActive();
    @Select("SELECT * FROM mr_token_package ORDER BY sort,id")
    List<TokenPackage> selectAll();
}