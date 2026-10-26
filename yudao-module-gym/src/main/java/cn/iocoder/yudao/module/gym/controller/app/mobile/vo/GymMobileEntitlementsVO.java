package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

public final class GymMobileEntitlementsVO {
    private GymMobileEntitlementsVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CardPage extends PageParam {
        @Positive private Long storeId;

        @Pattern(regexp = "pending|active|frozen|expired|exhausted|inactive")
        private String status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PackPage extends PageParam {
        @Positive private Long storeId;
        @Positive private Long coachId;
    }

    public record MemberCard(
            String id,
            String name,
            String type,
            String status,
            List<String> storeIds,
            String validFrom,
            String validUntil,
            Integer remainingTimes,
            String frozenReason) {}

    public record CoursePack(
            String id,
            String productId,
            String storeId,
            String coachId,
            int totalLessons,
            int remainingLessons,
            int reservedLessons,
            int availableLessons,
            String validUntil,
            String status) {}
}
