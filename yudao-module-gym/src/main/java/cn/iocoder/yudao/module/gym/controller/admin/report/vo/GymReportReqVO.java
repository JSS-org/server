package cn.iocoder.yudao.module.gym.controller.admin.report.vo;

import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GymReportReqVO extends GymBusinessPageReqVO {
    @Pattern(regexp = "store|employee")
    private String groupBy;

    @Min(1)
    @Max(366)
    private Integer days;
}
