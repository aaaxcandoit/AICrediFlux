package aicrediflux.token.flashsale.domain;

import java.time.Instant;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_flash_sale_order")
public class FlashSaleOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Integer userId;
    private Long packageId;
    private Long campaignId;
    private String packageName;
    private Long creditAmount;
    private Long payAmount;
    private String orderSource;
    private String orderStatus;
    private String payStatus;
    private String creditStatus;
    private Instant expireTime;
    private Instant payTime;
    private Instant closeTime;
    private Integer stockRestored;
    private Instant createTime;
    private Instant updateTime;
}
