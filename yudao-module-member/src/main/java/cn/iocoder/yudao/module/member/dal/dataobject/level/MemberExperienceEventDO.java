package cn.iocoder.yudao.module.member.dal.dataobject.level;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/** A receipt without logical deletion or public deletion, committed with the experience change. */
@Data
@TableName("member_experience_event")
public class MemberExperienceEventDO {
    @TableId private Long id;
    private Long userId;
    private Integer bizType;
    private String bizId;
    private Integer requestedExperience;
    private Long tenantId;
    private LocalDateTime createTime;
}
