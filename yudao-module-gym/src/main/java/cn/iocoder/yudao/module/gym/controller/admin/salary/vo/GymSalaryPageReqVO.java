package cn.iocoder.yudao.module.gym.controller.admin.salary.vo;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
@Data
@EqualsAndHashCode(callSuper = true)
public class GymSalaryPageReqVO extends PageParam {
    @Positive private Long storeId;
    @Positive private Long employeeId;
    @Positive private Long templateId;
    @Size(max = 100) private String keyword;
    @Pattern(regexp = "draft|published") private String status;
}
