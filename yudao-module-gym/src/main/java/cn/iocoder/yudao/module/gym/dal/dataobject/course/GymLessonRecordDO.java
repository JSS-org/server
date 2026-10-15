package cn.iocoder.yudao.module.gym.dal.dataobject.course;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_lesson_record")
public class GymLessonRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long memberId;
    private Long coachId;
    private Long bookingId;
    private Long checkinId;
    private Long memberCardId;
    private Long memberCoursePackId;
    private Integer sessionUnits;
    private java.time.Instant confirmedAt;
    private String method;
    private String status;
}
