package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

public final class GymMobileWorkRecordsVO {
    private GymMobileWorkRecordsVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Page extends PageParam {
        @NotNull @Positive private Long storeId;
        @NotNull @Positive private Long memberId;

        @Size(max = 24)
        private String status;
    }

    public static class MeasurementCreate extends GymMeasurementSaveReqVO {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw exception(MOBILE_REQUEST_INVALID);
        }
    }

    public static class PlanCreate extends GymTrainingPlanSaveReqVO {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw exception(MOBILE_REQUEST_INVALID);
        }
    }

    @Data
    public static class ContractCreate {
        @NotNull @Positive private Long storeId;
        @NotNull @Positive private Long memberId;

        @NotNull
        @Pattern(regexp = "member_card|course_pack")
        private String sourceType;

        @NotNull @Positive private Long sourceId;
        @NotNull @Positive private Long templateId;

        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw exception(MOBILE_REQUEST_INVALID);
        }
    }
}
