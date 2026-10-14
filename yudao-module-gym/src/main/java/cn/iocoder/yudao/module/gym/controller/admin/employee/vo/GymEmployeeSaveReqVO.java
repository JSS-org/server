package cn.iocoder.yudao.module.gym.controller.admin.employee.vo;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@Data
@Schema(description = "员工档案；工号不可变；账号关联须 system:user:update，工资配置暂不支持")
public class GymEmployeeSaveReqVO {
    @Positive private Long userId;
    @NotNull @Size(min = 1, max = 30) private List<@NotNull @Valid StoreAssignment> storeAssignments;
    @Schema(description = "保留检测字段，提交工资配置将明确拒绝")
    private Object salaryAssignment;
    @NotBlank @Size(max = 64) private String employeeNo;
    @NotBlank @Size(max = 80) private String name;
    @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") private String phone;
    @Size(max = 80) private String jobTitle;
    @NotNull private LocalDate hiredOn;
    @NotBlank @Pattern(regexp = "active|inactive") private String status;
    @Data
    public static class StoreAssignment {
        @NotNull @Positive private Long storeId;
        @NotNull private Boolean isPrimary;
        @NotNull private Boolean isManager;
        @NotNull private LocalDate joinedOn;
    }
}
