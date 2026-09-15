package aicrediflux.token.rag.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_rag_document")
public class RagDocument {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_INDEXING = "INDEXING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DELETED = "DELETED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String docNo;
    private String space;
    private String title;
    private String sourceType;
    private String sourceName;
    private String contentHash;
    private Integer version;
    private String status;
    private Integer chunkCount;
    private Integer createdBy;
    private Instant indexedAt;
    private String errorMessage;
    private Instant createTime;
    private Instant updateTime;
}
