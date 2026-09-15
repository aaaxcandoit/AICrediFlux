package aicrediflux.token.agent.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_agent_session")
public class AgentSession {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionNo;
    private Integer userId;
    private String title;
    private String model;
    private String status;
    private Instant createTime;
    private Instant updateTime;
}
