package aicrediflux.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import aicrediflux.token.pojo.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
