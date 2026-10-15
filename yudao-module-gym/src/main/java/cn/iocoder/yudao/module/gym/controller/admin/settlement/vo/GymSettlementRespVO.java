package cn.iocoder.yudao.module.gym.controller.admin.settlement.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
@Data public class GymSettlementRespVO {
 private String id; private String storeId; private String storeName; private String orderNo;
 private BigDecimal baseAmount; private Instant dueAt; private String status; private List<Item> items;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymSettlementRespVOItem")
 public static class Item {
  private String id; private String accountName; private String accountMasked;
  private BigDecimal ratio; private BigDecimal amount; private String status; private Instant settledAt; private String failureReason;
 }
}
