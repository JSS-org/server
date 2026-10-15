package cn.iocoder.yudao.module.gym.controller.admin.common;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
@Data
@EqualsAndHashCode(callSuper = true)
public class GymBusinessPageReqVO extends PageParam {
    @Positive private Long storeId;
    @Positive private Long memberId;
    @Positive private Long coachId;
    @Positive private Long sessionId;
    @Size(max=32) private String kind;
    @Size(max=32) private String businessType;
    @Size(max=16) private String applicableType;
    @Size(max = 100) private String keyword;
    @Size(max = 32) private String status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate date;
    @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") private String month;
    @Size(max=64) private String from;
    @Size(max=64) private String to;
    @Min(1) @Max(3650) private Integer cardExpiresWithinDays;
    @Min(1) @Max(3650) private Integer courseExpiresWithinDays;
    private Boolean hasPersonalTraining;
}
