package aicrediflux.token.rag.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_rag_index_job")
public class RagIndexJob {
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobNo;
    private String docNo;
    private String status;
    private Integer requestedBy;
    private Integer totalChunks;
    private Integer successChunks;
    private String failedReason;
    private Instant createTime;
    private Instant updateTime;
}
