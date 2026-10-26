package cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Schema(description = "美团门店验券历史响应，保留平台信封及业务字段")
public class GymMeituanCouponHistoryRespVO {
    @Schema(description = "美团响应码，成功为 OP_SUCCESS")
    private String code;

    private String msg;

    @Schema(description = "美团请求跟踪 ID")
    private String traceId;

    private History data;

    @Getter
    @Setter
    public static class History {
        private Long total;
        private List<Coupon> couponCodes;
    }

    @Getter
    @Setter
    public static class Coupon {
        @Schema(description = "团购券购买价格，沿用平台金额口径")
        private BigDecimal couponBuyPrice;

        @Schema(description = "是否可撤销：1 可撤销，0 不可撤销")
        private Long couponCancelStatus;

        @Schema(description = "团购券码，字符串保留前导零")
        private String couponCode;

        private String couponStatusDesc;

        @Schema(description = "平台返回的验券时间字符串")
        private String couponUseTime;

        private String dealBeginTime;
        private String dealTitle;

        @Schema(description = "项目售卖价，沿用平台金额口径")
        private BigDecimal dealValue;

        @Schema(description = "是否核销收尾款：1 是，2 否")
        private Long isVerifyPay;

        private Boolean isVoucher;
        private String verifyAcct;
        private String verifyType;
        private SqtInfo sqtInfo;
    }

    @Getter
    @Setter
    public static class SqtInfo {
        private Integer sqtOrder;
        private Integer sqtNeedInvoice;
    }
}
