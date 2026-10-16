package cn.iocoder.yudao.module.gym.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "员工档案；工号不可变；账号关联须 system:user:update，工资配置须 gym:payroll:templates 权限")
public class GymEmployeeSaveReqVO {
    @Positive private Long userId;

    @NotNull
    @Size(min = 1, max = 30)
    private List<@NotNull @Valid StoreAssignment> storeAssignments;

    @Valid
    private cn.iocoder.yudao.module.gym.controller.admin.salary.vo.GymSalaryAssignmentReqVO
            salaryAssignment;

    @NotBlank
    @Size(max = 64)
    private String employeeNo;

    @NotBlank
    @Size(max = 80)
    private String name;

    @NotBlank
    @Pattern(regexp = "1[3-9][0-9]{9}")
    private String phone;

    @Size(max = 80)
    private String jobTitle;

    @NotNull private LocalDate hiredOn;

    @NotBlank
    @Pattern(regexp = "active|inactive")
    private String status;

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymEmployeeSaveReqVOStoreAssignment")
    public static class StoreAssignment {
        @NotNull @Positive private Long storeId;
        @NotNull private Boolean isPrimary;
        @NotNull private Boolean isManager;
        @NotNull private LocalDate joinedOn;
    }
}
