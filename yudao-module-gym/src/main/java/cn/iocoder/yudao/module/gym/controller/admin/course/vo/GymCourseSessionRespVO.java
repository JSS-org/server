package cn.iocoder.yudao.module.gym.controller.admin.course.vo;

import lombok.Data;

@Data
public class GymCourseSessionRespVO {
    private String id;
    private String storeName;
    private String memberName;
    private String coachName;
    private String courseName;
    private String storeId;
    private String courseProductId;
    private String coachId;
    private String room;
    private java.time.Instant startsAt;
    private java.time.Instant endsAt;
    private Integer capacity;
    private Integer reservedCount;
    private String status;
}
