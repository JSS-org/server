package cn.iocoder.yudao.module.gym.dal.dataobject.course;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_checkin")
public class GymCheckinDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long memberId;
    private Long bookingId;
    private java.time.Instant enteredAt;
    private java.time.Instant exitedAt;
    private String method;
    private String visitorType;
    private String status;
}
