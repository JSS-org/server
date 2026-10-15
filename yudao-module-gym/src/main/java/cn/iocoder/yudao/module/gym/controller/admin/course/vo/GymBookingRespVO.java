package cn.iocoder.yudao.module.gym.controller.admin.course.vo;
import lombok.Data;
@Data
public class GymBookingRespVO {
    private String id;
    private String storeName;
    private String memberName;
    private String coachName;
    private String courseName;
    private java.time.Instant sessionStartsAt;
    private String storeId;
    private String sessionId;
    private String memberId;
    private String memberCardId;
    private String memberCoursePackId;
    private String status;
    private java.time.Instant bookedAt;
    private java.time.Instant cancelledAt;
    private String checkinId;
    private String note;
}
