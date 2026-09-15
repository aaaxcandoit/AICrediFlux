package aicrediflux.token.flashsale.domain;

import java.time.Instant;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mr_token_package")
public class TokenPackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Long creditAmount;
    private Long originalPrice;
    private Long salePrice;
    private Integer status;
    private Integer sort;
    private Instant createTime;
    private Instant updateTime;
}
