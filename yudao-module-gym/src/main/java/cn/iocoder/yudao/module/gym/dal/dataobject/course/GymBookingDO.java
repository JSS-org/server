package cn.iocoder.yudao.module.gym.dal.dataobject.course;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_booking")
public class GymBookingDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long sessionId;
    private Long memberId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long memberCardId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long memberCoursePackId;
    private String status;
    private java.time.Instant bookedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private java.time.Instant cancelledAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long checkinId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String note;
}
