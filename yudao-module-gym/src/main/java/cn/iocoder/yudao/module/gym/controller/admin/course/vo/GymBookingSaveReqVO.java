package cn.iocoder.yudao.module.gym.controller.admin.course.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data
public class GymBookingSaveReqVO {
    @NotNull @Positive private Long sessionId;
    @NotNull @Positive private Long memberId;
    @Positive private Long memberCardId;
    @Positive private Long memberCoursePackId;
    @Size(max=2000) private String note;
    @Pattern(regexp="cancelled") private String status;
}
