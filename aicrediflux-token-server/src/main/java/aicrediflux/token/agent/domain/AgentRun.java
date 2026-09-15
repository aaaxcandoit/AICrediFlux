package aicrediflux.token.agent.domain;

import java.time.Instant;
import java.util.Set;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_agent_run")
public class AgentRun {
    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_WAITING_TOOL = "WAITING_TOOL";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final Set<String> ACTIVE_STATUSES = Set.of(STATUS_CREATED, STATUS_RUNNING, STATUS_WAITING_TOOL);
    public static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED);

    @TableId(type = IdType.AUTO)
    private Long id;
    private String runNo;
    private String sessionNo;
    private Integer userId;
    private String model;
    private String status;
    private String requestId;
    private String source;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
    private Integer totalPromptTokens;
    private Integer totalCompletionTokens;
    private Long totalQuota;
    private Integer stopRequested;
    private Instant createTime;
    private Instant updateTime;
}
