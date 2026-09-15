package aicrediflux.token.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import aicrediflux.token.agent.domain.AgentMessage;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
    @Select("SELECT * FROM mr_agent_message WHERE session_no=#{sessionNo} ORDER BY id ASC")
    List<AgentMessage> selectBySessionNo(@Param("sessionNo") String sessionNo);
}
