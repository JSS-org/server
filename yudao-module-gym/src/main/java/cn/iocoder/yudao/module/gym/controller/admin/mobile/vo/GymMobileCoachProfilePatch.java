package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;

import lombok.Getter;

import java.util.List;

@Getter
@io.swagger.v3.oas.annotations.media.Schema(description = "仅修改本人教练资料；省略保留，头像null清除，介绍和列表不接受null")
public class GymMobileCoachProfilePatch {
    @Positive private Long avatarFileId;

    @Size(max = 4000)
    private String introduction;

    @Size(max = 20)
    private List<@NotBlank @Size(max = 80) String> specialties;

    @Size(max = 20)
    private List<@NotNull @Positive Long> qualificationFileIds;

    @JsonIgnore private boolean avatarPresent;
    @JsonIgnore private boolean introductionPresent;
    @JsonIgnore private boolean specialtiesPresent;
    @JsonIgnore private boolean qualificationsPresent;

    public void setAvatarFileId(Long value) {
        avatarFileId = value;
        avatarPresent = true;
    }

    public void setIntroduction(String value) {
        introduction = value;
        introductionPresent = true;
    }

    public void setSpecialties(List<String> value) {
        specialties = value;
        specialtiesPresent = true;
    }

    public void setQualificationFileIds(List<Long> value) {
        qualificationFileIds = value;
        qualificationsPresent = true;
    }

    @JsonAnySetter
    public void unknown(String name, Object value) {
        throw exception(MOBILE_REQUEST_INVALID);
    }
}
