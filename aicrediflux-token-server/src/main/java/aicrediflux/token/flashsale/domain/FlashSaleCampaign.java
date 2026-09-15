package aicrediflux.token.flashsale.domain;

import java.time.Instant;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_flash_sale_campaign")
public class FlashSaleCampaign {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long packageId;
    private String activityName;
    private Long flashPrice;
    private Long creditAmount;
    private Integer totalStock;
    private Integer availableStock;
    private Integer perUserLimit;
    private Instant startTime;
    private Instant endTime;
    private Integer status;
    private Integer version;
    private Instant createTime;
    private Instant updateTime;
}
