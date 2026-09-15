package aicrediflux.token.rag.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_rag_chunk")
public class RagChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String docNo;
    private Integer version;
    private Integer chunkNo;
    private String locator;
    private String contentHash;
    private String text;
    private String metadataJson;
    private String vectorId;
    private Instant createTime;
}
