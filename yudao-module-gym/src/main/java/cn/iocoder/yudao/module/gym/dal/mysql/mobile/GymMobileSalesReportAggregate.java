package cn.iocoder.yudao.module.gym.dal.mysql.mobile;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class GymMobileSalesReportAggregate {
    private GymMobileSalesReportAggregate() {}

    public record Criteria(
            Long tenant,
            List<Long> stores,
            Instant from,
            Instant to,
            String product,
            Long employee) {}

    @Data
    public static class Row {
        private Long id;
        private Long orderId;
        private Long storeId;
        private Long employeeId;
        private String orderNo;
        private String productType;
        private BigDecimal paidAmount;
        private BigDecimal refundedAmount;
        private Instant paidAt;
        private Instant latestAt;
    }

    @Data
    public static class Totals {
        private BigDecimal paidAmount;
        private BigDecimal refundedAmount;
        private Long paidOrderCount;
        private BigDecimal cardAmount;
        private Long cardOrderCount;
        private BigDecimal courseAmount;
        private Long courseOrderCount;
    }
}
