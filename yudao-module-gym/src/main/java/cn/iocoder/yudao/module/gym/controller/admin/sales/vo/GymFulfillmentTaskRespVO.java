package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;
import lombok.Data;
import java.time.Instant;
@Data public class GymFulfillmentTaskRespVO {
 private String id; private String orderId; private String status;
 private Integer attemptCount; private Integer attemptLimit;
 private Instant nextAttemptAt; private Instant lastAttemptAt; private String failureReason;
}
