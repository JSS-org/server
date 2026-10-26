package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.StrictRequest;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.*;

import lombok.*;

import java.util.*;

public final class GymMobileApprovalsVO {
    private GymMobileApprovalsVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @Positive private Long storeId;

        @NotNull
        @Pattern(regexp = "mine|pending|processed")
        private String view;

        @Pattern(regexp = "pending|approved|rejected")
        private String status;
    }

    @Data
    public static class OptionsQuery {
        @NotNull @Positive private Long storeId;

        @NotNull
        @Pattern(regexp = "card_opening|price_change|contract|other_configured")
        private String businessType;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class Decision extends StrictRequest {
        @NotNull
        @Pattern(regexp = "approve|reject")
        private String decision;

        @Size(max = 2000)
        private String comment;

        @NotNull @Positive private Integer expectedVersion;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Step(
            int step, String approverId, String status, String comment, String decidedAt) {}

    public record Approval(
            String id,
            String businessType,
            String storeId,
            String applicantId,
            String status,
            int currentStep,
            int version,
            Map<String, Object> businessSummary,
            List<Step> steps,
            String createdAt) {}

    public record Template(String id, String name, int version) {}

    public record Options(List<Template> templates, List<String> businessTypes) {}
}
