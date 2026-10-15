package cn.iocoder.yudao.module.gym.controller.admin.salary.vo;
import lombok.Data;
import java.time.LocalDate;
@Data
public class GymSalaryAssignmentRespVO {
    private String id;
    private String employeeId;
    private String employeeName;
    private String storeId;
    private String storeName;
    private String templateId;
    private String templateName;
    private Integer templateVersion;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
