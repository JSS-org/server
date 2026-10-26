package cn.iocoder.yudao.module.gym.controller.admin.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.*;

import java.util.List;

public class GymMobileRiskVO {
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @NotNull @Positive private Long storeId;

        @Pattern(regexp = "open|resolved")
        private String status;
    }

    @Data
    public static class Resolve {
        @NotNull
        @Pattern(regexp = "confirmed|false_positive")
        private String result;

        @NotBlank
        @Size(max = 2000)
        private String note;

        @NotNull
        @Min(1)
        private Integer expectedVersion;
    }

    public record Resolution(
            String result, String note, String operatorUserId, String resolvedAt) {}

    public record Alert(
            String id,
            String type,
            String deviceId,
            String memberId,
            String occurredAt,
            String status,
            List<String> evidenceFileIds,
            int version,
            Resolution resolution) {}
}
