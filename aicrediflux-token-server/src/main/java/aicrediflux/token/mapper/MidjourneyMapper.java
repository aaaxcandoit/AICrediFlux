package aicrediflux.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import aicrediflux.token.pojo.entity.Midjourney;

/**
 * Midjourney Mapper  */
@Mapper
public interface MidjourneyMapper extends BaseMapper<Midjourney> {
}
