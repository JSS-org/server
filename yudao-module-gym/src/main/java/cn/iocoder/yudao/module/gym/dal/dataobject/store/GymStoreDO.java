package cn.iocoder.yudao.module.gym.dal.dataobject.store;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_store")
public class GymStoreDO extends TenantBaseDO {
    @TableId(type = IdType.INPUT) private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal areaSqm;
    private String features;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String servicePhone;
    private String serviceWechat;
    private String opens;
    private String closes;
    private String status;
}
