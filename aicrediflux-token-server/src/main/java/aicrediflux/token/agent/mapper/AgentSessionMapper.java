package aicrediflux.token.agent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import aicrediflux.token.agent.domain.AgentSession;

@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {
    @Select("SELECT * FROM mr_agent_session WHERE session_no=#{sessionNo} AND user_id=#{userId} AND status='ACTIVE' LIMIT 1")
    AgentSession selectOwned(@Param("sessionNo") String sessionNo, @Param("userId") int userId);

    @Select("SELECT * FROM mr_agent_session WHERE user_id=#{userId} AND status='ACTIVE' ORDER BY update_time DESC, id DESC LIMIT #{limit}")
    List<AgentSession> selectByUserId(@Param("userId") int userId, @Param("limit") int limit);

    @Update("UPDATE mr_agent_session SET title=#{title}, update_time=#{updateTime} WHERE session_no=#{sessionNo} AND user_id=#{userId} AND status='ACTIVE'")
    int updateTitle(@Param("sessionNo") String sessionNo, @Param("userId") int userId,
                    @Param("title") String title, @Param("updateTime") java.time.Instant updateTime);
    @Update("UPDATE mr_agent_session SET status='ARCHIVED', update_time=#{updateTime} WHERE session_no=#{sessionNo} AND user_id=#{userId} AND status='ACTIVE'")
    int archiveOwned(@Param("sessionNo") String sessionNo, @Param("userId") int userId,
                     @Param("updateTime") java.time.Instant updateTime);
}
