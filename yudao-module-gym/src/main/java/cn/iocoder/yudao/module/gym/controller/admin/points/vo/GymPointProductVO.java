package cn.iocoder.yudao.module.gym.controller.admin.points.vo;

import jakarta.validation.constraints.*;

import lombok.Data;

public class GymPointProductVO {
    @Data
    public static class Save {
        @NotNull @Positive private Long storeId;
        @NotNull @Positive private Long cardProductId;

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotNull
        @Min(1)
        @Max(9007199254740991L)
        private Long requiredPoints;

        @Min(0)
        private Integer stock;

        @NotNull private Boolean enabled;

        @Min(1)
        @Max(9007199254740991L)
        private Long revision;
    }

    public record Product(
            String id,
            String storeId,
            String cardProductId,
            String name,
            long requiredPoints,
            Integer stock,
            boolean enabled,
            long revision) {}
}
