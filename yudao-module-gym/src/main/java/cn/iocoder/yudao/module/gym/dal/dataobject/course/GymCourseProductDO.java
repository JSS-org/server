package cn.iocoder.yudao.module.gym.dal.dataobject.course;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_course_product")
public class GymCourseProductDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long coachId;
    private String name;
    private String description;
    private String kind;
    private Integer sessionCount;
    private Integer durationMinutes;
    private java.math.BigDecimal price;
    private String status;
}
