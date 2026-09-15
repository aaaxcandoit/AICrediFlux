package aicrediflux.token.agent.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_agent_message")
public class AgentMessage {
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageNo;
    private String sessionNo;
    private Integer userId;
    private String role;
    private String content;
    private String toolCallId;
    private String metadata;
    private String usageJson;
    private Instant createTime;
}
