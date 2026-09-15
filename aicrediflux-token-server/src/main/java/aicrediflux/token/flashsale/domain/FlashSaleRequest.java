package aicrediflux.token.flashsale.domain;

import java.time.Instant;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_flash_sale_request")
public class FlashSaleRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestNo;
    private String orderNo;
    private Long campaignId;
    private Integer userId;
    private String processStatus;
    private String failReason;
    private Instant createTime;
    private Instant updateTime;
}
