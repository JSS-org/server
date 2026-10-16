package cn.iocoder.yudao.module.gym.controller.admin.course.vo;

import lombok.Data;

@Data
public class GymLessonRecordRespVO {
    private String id;
    private String storeName;
    private String memberName;
    private String coachName;
    private String storeId;
    private String memberId;
    private String coachId;
    private String bookingId;
    private String checkinId;
    private String memberCardId;
    private String memberCoursePackId;
    private Integer sessionUnits;
    private java.time.Instant confirmedAt;
    private String method;
    private String status;
}
