package cn.iocoder.yudao.module.gym.controller.admin.course.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
@Data
public class GymCourseProductSaveReqVO {
    @NotNull @Positive private Long storeId;
    @NotNull @Positive private Long coachId;
    @NotBlank @Size(max=100) private String name;
    @Size(max=2000) private String description;
    @NotNull @Pattern(regexp="private") private String kind;
    @NotNull @Min(1) @Max(100000) private Integer sessionCount;
    @NotNull @Min(1) @Max(480) private Integer durationMinutes;
    @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) private BigDecimal price;
    @NotNull @Pattern(regexp="active|inactive") private String status;
}
