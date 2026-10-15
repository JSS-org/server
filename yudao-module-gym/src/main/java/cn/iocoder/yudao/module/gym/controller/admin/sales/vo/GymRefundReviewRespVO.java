package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;
import lombok.Data;
import java.time.Instant;
import java.util.List;
@Data public class GymRefundReviewRespVO {
 private String id; private String orderId; private String refundId; private String status;
 private String decision; private List<String> memberCardIds; private String note;
 private String reviewerId; private Instant reviewedAt; private Instant createdAt;
}
