package cn.iocoder.yudao.module.member.controller.admin.user.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 会员用户更新 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MemberUserUpdateReqVO extends MemberUserBaseVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "23788")
    @NotNull(message = "编号不能为空")
    private Long id;

    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean birthdayPresent;

    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean areaIdPresent;

    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean groupIdPresent;

    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private boolean tagIdsPresent;

    @Override
    public MemberUserUpdateReqVO setBirthday(LocalDateTime value) {
        super.setBirthday(value);
        birthdayPresent = true;
        return this;
    }

    @Override
    public MemberUserUpdateReqVO setAreaId(Long value) {
        super.setAreaId(value);
        areaIdPresent = true;
        return this;
    }

    @Override
    public MemberUserUpdateReqVO setGroupId(Long value) {
        super.setGroupId(value);
        groupIdPresent = true;
        return this;
    }

    @Override
    public MemberUserUpdateReqVO setTagIds(List<Long> value) {
        super.setTagIds(value);
        tagIdsPresent = true;
        return this;
    }
}
