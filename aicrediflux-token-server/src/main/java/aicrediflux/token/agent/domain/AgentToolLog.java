package aicrediflux.token.agent.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_agent_tool_log")
public class AgentToolLog {
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DENIED = "DENIED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String toolLogNo;
    private String runNo;
    private String sessionNo;
    private Integer userId;
    private String toolName;
    private String toolCallId;
    private String status;
    private String inputJson;
    private String outputJson;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createTime;
    private Instant updateTime;
}
