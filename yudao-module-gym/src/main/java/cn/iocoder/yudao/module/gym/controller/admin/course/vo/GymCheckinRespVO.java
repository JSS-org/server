package cn.iocoder.yudao.module.gym.controller.admin.course.vo;
import lombok.Data;
@Data
public class GymCheckinRespVO {
    private String id;
    private String storeName;
    private String memberName;
    private String coachName;
    private String storeId;
    private String memberId;
    private String bookingId;
    private java.time.Instant enteredAt;
    private java.time.Instant exitedAt;
    private String method;
    private String visitorType;
    private String status;
}
