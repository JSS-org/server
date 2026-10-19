package cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Data
@Schema(description = "管理后台 - 美团门店验券历史查询")
public class GymMeituanCouponHistoryReqVO {
    @NotNull
    @Positive
    @Schema(description = "本系统门店 ID，服务端据此查找当前租户的美团授权", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long storeId;

    @NotBlank
    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])")
    @Schema(
            description = "查询日期 yyyy-MM-dd",
            example = "2026-10-19",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String date;

    @NotNull
    @Min(0)
    @Schema(description = "查询起始位置，从 0 开始", defaultValue = "0")
    private Integer offset = 0;

    @NotNull
    @Min(1)
    @Max(200)
    @Schema(description = "查询条数，本系统按美团建议限制为 1 至 200", defaultValue = "20")
    private Integer limit = 20;

    @AssertTrue(message = "date 必须为有效日期")
    @Schema(hidden = true)
    public boolean isDateValid() {
        if (date == null || date.isBlank()) return true;
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
