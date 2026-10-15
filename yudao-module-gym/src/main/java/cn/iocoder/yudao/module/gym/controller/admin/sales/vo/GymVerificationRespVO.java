package cn.iocoder.yudao.module.gym.controller.admin.sales.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
@Data public class GymVerificationRespVO {
 private String id;
 private String storeId;
 private String storeName;
 private String memberId;
 private String memberName;
 private String type;
 private String channel;
 private String status;
 private String memberCardId;
 private String checkinId;
 private Instant verifiedAt;
 private String failureReason;
}
