package cn.iocoder.yudao.module.gym.dal.dataobject.card;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gym_member_card_store")
public class GymMemberCardStoreDO extends TenantBaseDO {
    @TableId private Long id;
    private Long cardId;
    private Long storeId;
}
