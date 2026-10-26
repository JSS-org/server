package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto;

import lombok.Getter;
import lombok.Setter;

/** 美团 queryListByDate 的 biz 参数，不携带本系统门店/租户字段。 */
@Getter
@Setter
public class MeituanCouponHistoryRequest {
    private String date;
    private Integer offset;
    private Integer limit;
}
