package cn.iocoder.yudao.module.gym.controller.app.mobile.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

public final class GymMobileCatalogVO {
    private GymMobileCatalogVO() {}

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Schema(name = "GymMobileCatalogStorePage")
    public static class StorePage extends PageParam {
        @NotNull @Positive private Long storeId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Schema(name = "GymMobileCatalogCoursePage")
    public static class CoursePage extends StorePage {
        @Positive private Long coachId;
    }

    @Schema(name = "GymMobileCatalogProduct")
    public record Product(
            String id,
            String type,
            String storeId,
            String name,
            String description,
            String price,
            int version,
            Integer validDays,
            Integer lessonCount,
            String coachId,
            String saleStatus,
            String purchaseLimitReason,
            String renewalTerms,
            String agreementVersion,
            Long saleRevision,
            String cardType,
            Integer includedUses,
            Integer billingDay,
            Integer renewalNoticeDays,
            Integer graceDays) {}
}
