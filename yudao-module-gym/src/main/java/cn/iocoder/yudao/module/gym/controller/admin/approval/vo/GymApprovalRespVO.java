package cn.iocoder.yudao.module.gym.controller.admin.approval.vo;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileRespVO;
@Data
public class GymApprovalRespVO {
 private String id;
 private String storeId;
 private String storeName;
 private String templateId;
 private String businessType;
 private String title;
 private String applicantName;
 private String approverName;
 private String status;
 private Integer currentStep;
 private Instant submittedAt;
 private Instant completedAt;
 private Boolean canDecide;
 private Map<String,Object> requestSnapshot;
 private Map<String,Object> businessResult;
 private List<GymFileRespVO> attachments;
 private List<Task> tasks;
 @Data @io.swagger.v3.oas.annotations.media.Schema(name = "GymApprovalRespVOTask")
 public static class Task {
  private String id;
  private Integer stepNo;
  private String approverEmployeeId;
  private String approverName;
  private String status;
  private String comment;
  private Instant decidedAt;
 }
}
