package cn.iocoder.yudao.module.gym.controller.admin.approval.vo;
import lombok.Data;
import java.util.List;
@Data
public class GymApprovalTemplateRespVO {
    private String id;
    private String familyId;
    private Integer version;
    private String name;
    private String businessType;
    private String status;
    private List<Step> steps;
    @Data
    @io.swagger.v3.oas.annotations.media.Schema(name = "GymApprovalTemplateRespVOStep")
 public static class Step {
        private Integer stepNo;
        private String approverEmployeeId;
        private String approverName;
    }
}
