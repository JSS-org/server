package cn.iocoder.yudao.module.gym.dal.dataobject.course;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_course_pack")
public class GymCoursePackDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long memberId;
    private Long courseProductId;
    private Long assignedCoachId;
    private String name;
    private java.time.Instant startsAt;
    private java.time.Instant expiresAt;
    private Integer totalSessions;
    private Integer remainingSessions;
    private Integer reservedSessions;
    private String status;
    private java.math.BigDecimal priceSnapshot;
    private String sourceType;
    private String sourceId;
}
