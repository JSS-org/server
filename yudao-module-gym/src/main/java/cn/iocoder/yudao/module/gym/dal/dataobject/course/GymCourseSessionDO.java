package cn.iocoder.yudao.module.gym.dal.dataobject.course;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_course_session")
public class GymCourseSessionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long courseProductId;
    private Long coachId;
    private String room;
    private java.time.Instant startsAt;
    private java.time.Instant endsAt;
    private Integer capacity;
    private Integer reservedCount;
    private String status;
}
