package cn.iocoder.yudao.module.gym.controller.admin.course.vo;
import lombok.Data;
@Data
public class GymCoursePackRespVO {
    private String id;
    private String storeName;
    private String memberName;
    private String coachName;
    private String assignedCoachName;
    private String storeId;
    private String memberId;
    private String courseProductId;
    private String assignedCoachId;
    private String name;
    private java.time.Instant startsAt;
    private java.time.Instant expiresAt;
    private Integer totalSessions;
    private Integer remainingSessions;
    private Integer reservedSessions;
    private String status;
    private java.math.BigDecimal priceSnapshot;
}
