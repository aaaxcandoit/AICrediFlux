package aicrediflux.token.agent.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import aicrediflux.token.agent.domain.AgentRun;

@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRun> {
    @Select("SELECT * FROM mr_agent_run WHERE session_no=#{sessionNo} AND status IN ('CREATED','RUNNING','WAITING_TOOL') ORDER BY id DESC LIMIT 1")
    AgentRun selectRunningBySessionNo(@Param("sessionNo") String sessionNo);

    @Select("SELECT * FROM mr_agent_run WHERE run_no=#{runNo} AND user_id=#{userId} LIMIT 1")
    AgentRun selectOwned(@Param("runNo") String runNo, @Param("userId") int userId);

    @Select("SELECT * FROM mr_agent_run WHERE run_no=#{runNo} LIMIT 1")
    AgentRun selectByRunNo(@Param("runNo") String runNo);

    @Update("UPDATE mr_agent_run SET stop_requested=1, update_time=#{now} WHERE run_no=#{runNo} AND user_id=#{userId} AND status IN ('CREATED','RUNNING','WAITING_TOOL')")
    int requestStop(@Param("runNo") String runNo, @Param("userId") int userId, @Param("now") Instant now);

    @Update("UPDATE mr_agent_run SET status=#{status}, finished_at=#{now}, error_message=#{errorMessage}, update_time=#{now} WHERE run_no=#{runNo} AND status IN ('CREATED','RUNNING','WAITING_TOOL')")
    int finish(@Param("runNo") String runNo, @Param("status") String status, @Param("errorMessage") String errorMessage, @Param("now") Instant now);

    @Update("UPDATE mr_agent_run SET status='COMPLETED', finished_at=#{now}, total_prompt_tokens=#{promptTokens}, "
            + "total_completion_tokens=#{completionTokens}, total_quota=#{quota}, update_time=#{now} "
            + "WHERE run_no=#{runNo} AND status IN ('CREATED','RUNNING','WAITING_TOOL')")
    int completeSuccess(@Param("runNo") String runNo, @Param("promptTokens") int promptTokens,
                        @Param("completionTokens") int completionTokens, @Param("quota") long quota,
                        @Param("now") Instant now);

    @Update("UPDATE mr_agent_run SET status='WAITING_TOOL', update_time=#{now} WHERE run_no=#{runNo} AND status='RUNNING'")
    int markWaitingTool(@Param("runNo") String runNo, @Param("now") Instant now);

    @Update("UPDATE mr_agent_run SET status='RUNNING', update_time=#{now} WHERE run_no=#{runNo} AND status='WAITING_TOOL'")
    int markRunning(@Param("runNo") String runNo, @Param("now") Instant now);
}
