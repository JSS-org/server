package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeituanMobileReceiptsRequest {
    @NotBlank
    @Pattern(regexp = "(?:[0-9]{11}|[0-9]+_[0-9]+)")
    private String mobile;

    @NotNull @Positive private Long dealGroupId;
    @NotNull @Positive private Long dealId;

    @NotNull
    @Min(0)
    private Integer offset = 0;

    @NotNull
    @Min(1)
    @Max(30)
    private Integer limit = 20;

    @NotNull
    @Min(1)
    @Max(2)
    private Integer platform;
}
