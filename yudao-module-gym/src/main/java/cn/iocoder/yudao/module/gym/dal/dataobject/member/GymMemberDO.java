package cn.iocoder.yudao.module.gym.dal.dataobject.member;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.*;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_member")
public class GymMemberDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private String memberNo;
    private String name;
    private String phone;
    private String nickname;
    private String gender;
    private LocalDate birthday;
    private String status;
    private String customerStage;
    private Instant registeredAt;
}
