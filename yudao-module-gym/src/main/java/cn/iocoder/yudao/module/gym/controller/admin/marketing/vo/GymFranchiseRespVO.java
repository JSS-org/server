package cn.iocoder.yudao.module.gym.controller.admin.marketing.vo;
import lombok.Data;
import java.time.Instant;
@Data public class GymFranchiseRespVO {
 private String id;private String contactName;private String phone;private String preferredCity;private String message;
 private String status;private Instant receivedAt;private Instant contactedAt;private Instant closedAt;private String handledBy;
}
