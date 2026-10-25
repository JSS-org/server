package cn.iocoder.yudao.module.gym.dal.dataobject.course;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_course_quote")
public class GymCourseQuoteDO extends TenantBaseDO {
    @TableId private Long id;
    private Long storeId;
    private Long productId;
    private Integer productVersion;
    private Long memberId;
    private Long approvalId;
    private Long issuerUserId;
    private BigDecimal amount;
    private String termsSnapshot;
    private Instant issuedAt;
    private Instant expiresAt;
    // Set only by the eventual transactional order checkout, never by either quote HTTP endpoint.
    private Long usedOrderId;
}
