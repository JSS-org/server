package cn.iocoder.yudao.module.gym.dal.dataobject.store;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_store_media")
public class GymStoreMediaDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long fileId;
    private String caption;
    private Boolean isCover;
    private Integer sortOrder;
}
