package cn.iocoder.yudao.module.gym.dal.dataobject.card;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_member_card")
public class GymMemberCardDO extends TenantBaseDO {
    @TableId private Long id;
    private Long memberId;
    private Long issuedStoreId;
    private Long templateId;
    private String cardNo;
    private String displayPrefix;
    private String name;
    private String kind;
    private String accessScope;
    private String storeIds;
    private java.time.Instant startsAt;
    private java.time.Instant expiresAt;
    private Integer totalUses;
    private Integer remainingUses;
    private Integer reservedUses;
    private String status;
    private String templateSnapshot;
    private String sourceType;
    private String sourceId;
}
