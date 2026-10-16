package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;

import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;

import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GymSalesRecordPageReqVO extends GymBusinessPageReqVO {
    @Size(max = 32)
    private String channel;

    @Size(max = 32)
    private String type;
}
