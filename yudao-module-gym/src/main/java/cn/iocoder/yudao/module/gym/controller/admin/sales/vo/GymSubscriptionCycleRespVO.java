package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.*;

@Data
public class GymSubscriptionCycleRespVO {
    private String id;
    private String subscriptionId;
    private String orderId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private String status;
    private Instant chargedAt;
    private String failureReason;
}
