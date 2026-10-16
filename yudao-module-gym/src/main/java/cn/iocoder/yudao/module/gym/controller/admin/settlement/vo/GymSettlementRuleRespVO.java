package cn.iocoder.yudao.module.gym.controller.admin.settlement.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class GymSettlementRuleRespVO {
    private String id;
    private String storeId;
    private Integer version;
    private String cycleUnit;
    private Integer cycleValue;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private List<Item> items;

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymSettlementRuleRespVOItem")
    public static class Item {
        private String paymentAccountId;
        private String accountName;
        private String accountMasked;
        private BigDecimal ratio;
    }
}
