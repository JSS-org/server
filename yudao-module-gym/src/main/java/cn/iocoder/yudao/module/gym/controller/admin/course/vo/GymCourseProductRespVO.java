package cn.iocoder.yudao.module.gym.controller.admin.course.vo;
import lombok.Data;
@Data
public class GymCourseProductRespVO {
    private String id;
    private String storeName;
    private String memberName;
    private String coachName;
    private String storeId;
    private String coachId;
    private String name;
    private String description;
    private String kind;
    private Integer sessionCount;
    private Integer durationMinutes;
    private java.math.BigDecimal price;
    private String status;
}
