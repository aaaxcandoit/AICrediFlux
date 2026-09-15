package aicrediflux.token.flashsale.domain;

import java.time.Instant;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_flash_sale_outbox")
public class FlashSaleOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String aggregateNo;
    private String eventType;
    private String topic;
    private String payload;
    private Instant deliverAt;
    private String publishStatus;
    private Integer retryCount;
    private Instant nextRetryTime;
    private Instant publishedTime;
    private Instant createTime;
    private Instant updateTime;
}
