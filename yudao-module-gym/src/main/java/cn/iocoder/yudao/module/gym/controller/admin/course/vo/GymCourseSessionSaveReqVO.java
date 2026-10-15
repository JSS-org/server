package cn.iocoder.yudao.module.gym.controller.admin.course.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.Instant;
@Data
public class GymCourseSessionSaveReqVO {
    @NotNull @Positive private Long storeId;
    @NotNull @Positive private Long courseProductId;
    @NotNull @Positive private Long coachId;
    @Size(max=100) private String room;
    @NotNull private Instant startsAt;
    @NotNull private Instant endsAt;
    @NotNull @Min(1) @Max(1) private Integer capacity;
    @NotNull @Pattern(regexp="scheduled|cancelled") private String status;
}
