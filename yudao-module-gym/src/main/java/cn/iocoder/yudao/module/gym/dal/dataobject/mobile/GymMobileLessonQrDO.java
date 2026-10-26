package cn.iocoder.yudao.module.gym.dal.dataobject.mobile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_mobile_lesson_qr")
public class GymMobileLessonQrDO extends TenantBaseDO {
    @TableId private Long id;
    private String tokenHash;
    private Long issuerUserId;
    private Long storeId;
    private Long coachId;
    private Long sessionId;
    private Long bookingId;
    private Long memberId;
    private Instant bookingBookedAt;
    private Instant sessionStartsAt;
    private Instant sessionEndsAt;
    private Instant expiresAt;
    private Instant usedAt;
    private Long lessonRecordId;
}
