package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeituanProductsRequest {
    @Min(1)
    private Integer pageNo;

    @Min(1)
    private Integer pageSize;
}
