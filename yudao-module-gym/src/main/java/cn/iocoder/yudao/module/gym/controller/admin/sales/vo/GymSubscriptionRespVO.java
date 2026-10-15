package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
@Data public class GymSubscriptionRespVO {
 private String id;
 private String storeId;
 private String storeName;
 private String memberId;
 private String memberName;
 private String cardTemplateId;
 private String channel;
 private String status;
 private BigDecimal amount;
 private Integer billingDay;
 private Instant requestedAt;
 private Instant signedAt;
 private Instant nextChargeAt;
 private Instant cancelledAt;
 private String failureReason;
}
