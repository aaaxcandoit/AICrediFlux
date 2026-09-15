package aicrediflux.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import aicrediflux.token.pojo.entity.Log;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogMapper extends BaseMapper<Log> {
}
